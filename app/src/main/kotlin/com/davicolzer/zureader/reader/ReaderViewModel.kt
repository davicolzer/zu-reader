package com.davicolzer.zureader.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davicolzer.zureader.ZuReaderApplication
import com.davicolzer.zureader.data.HistoryItem
import com.davicolzer.zureader.data.Settings
import com.davicolzer.zureader.data.ThemeMode
import com.davicolzer.zureader.vision.ImageReading
import com.davicolzer.zureader.vision.Quad
import com.davicolzer.zureader.vision.RgbImage
import com.davicolzer.zureader.vision.ZuImageReader
import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuGlyph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderUiState {
    data object Home : ReaderUiState
    data object Camera : ReaderUiState
    /** Recorte de uma foto ([fromCamera]) ou de uma imagem da galeria, com os cantos sugeridos. */
    data class Crop(val image: Bitmap, val rgb: RgbImage, val suggestion: Quad, val fromCamera: Boolean) : ReaderUiState
    data object Reading : ReaderUiState

    /**
     * [savedId] não nulo = leitura que está no histórico; correções são gravadas nela.
     * [crop] guarda a imagem original e o recorte usado, para o usuário poder ajustar e ler de novo.
     */
    data class Result(
        val image: Bitmap,
        val result: ImageReading,
        val savedId: Long? = null,
        val crop: Crop? = null,
    ) : ReaderUiState
    data object History : ReaderUiState
    data object Settings : ReaderUiState
    data object CouldNotOpen : ReaderUiState
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ZuReaderApplication
    private val history = app.history

    val settings: StateFlow<Settings> =
        app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** Glifos aprendidos com as correções do usuário. */
    val learned: StateFlow<List<ZuGlyph>> =
        app.learned.learned.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Leitor com o modo e a direção escolhidos e os glifos aprendidos; refeito quando algo muda. */
    private val imageReader: ZuImageReader
        get() {
            val key = ReaderKey(settings.value.mode, settings.value.direction, learned.value)
            readerCache?.let { (k, reader) -> if (k == key) return reader }
            return ZuImageReader.forMode(key.mode, key.direction, key.learned).also { readerCache = key to it }
        }

    private data class ReaderKey(val mode: ReadingMode, val direction: ReadingDirection, val learned: List<ZuGlyph>)

    @Volatile
    private var readerCache: Pair<ReaderKey, ZuImageReader>? = null
    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Home)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val recent: StateFlow<List<HistoryItem>> =
        history.recent(limit = 3).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allHistory: StateFlow<List<HistoryItem>> =
        history.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Imagem da galeria: lê direto. */
    /** Imagem da galeria: abre o recorte, com os cantos sugeridos em volta do texto. */
    fun read(uri: Uri) = work {
        val bitmap = withContext(Dispatchers.IO) { ImageLoading.decode(getApplication(), uri) }
        prepareCrop(bitmap, fromCamera = false)
    }

    fun openCamera() {
        _state.value = ReaderUiState.Camera
    }

    /** Foto da câmera: abre o recorte, com os cantos sugeridos em volta do texto. */
    fun onCaptured(photo: Bitmap) = work { prepareCrop(photo, fromCamera = true) }

    private suspend fun prepareCrop(image: Bitmap, fromCamera: Boolean): ReaderUiState.Crop =
        withContext(Dispatchers.Default) {
            val rgb = ImageLoading.toRgb(image)
            // leitura rápida só para sugerir os cantos
            val quick = imageReader.read(rgb, deskew = false, thorough = false)
            val suggestion = quick.suggestedCrop() ?: Quad.inset(rgb.width, rgb.height, 0.08f)
            ReaderUiState.Crop(image, rgb, suggestion, fromCamera)
        }

    /** Endireita o quadrilátero escolhido e lê. */
    fun confirmCrop(quad: Quad) {
        val crop = _state.value as? ReaderUiState.Crop ?: return
        work {
            withContext(Dispatchers.Default) {
                val rectified = crop.rgb.rectify(quad)
                ReaderUiState.Result(ImageLoading.toBitmap(rectified), imageReader.read(rectified), crop = crop.copy(suggestion = quad))
            }
        }
    }

    fun useWholePhoto() {
        val crop = _state.value as? ReaderUiState.Crop ?: return
        work {
            withContext(Dispatchers.Default) {
                val whole = Quad.inset(crop.rgb.width, crop.rgb.height, 0f)
                ReaderUiState.Result(crop.image, imageReader.read(crop.rgb), crop = crop.copy(suggestion = whole))
            }
        }
    }

    /** Volta da leitura para o recorte, com os cantos que foram usados. */
    fun adjustCrop() {
        val crop = (_state.value as? ReaderUiState.Result)?.crop ?: return
        _state.value = crop
    }

    /** Sai do recorte: volta para a câmera se a imagem veio dela, senão para o início. */
    fun leaveCrop() {
        val crop = _state.value as? ReaderUiState.Crop ?: return
        _state.value = if (crop.fromCamera) ReaderUiState.Camera else ReaderUiState.Home
    }

    /**
     * Troca a letra do glifo [index]; se a leitura já está salva, grava a correção.
     * O desenho do glifo (na orientação de leitura) vira um exemplo aprendido para as próximas leituras.
     */
    fun correct(index: Int, letter: Char) {
        val current = _state.value as? ReaderUiState.Result ?: return
        val reading = current.result.reading
        val glyph = reading.glyphs.getOrNull(index) ?: return
        val updated = current.copy(result = current.result.copy(reading = reading.withLetter(index, letter)))
        _state.value = updated
        viewModelScope.launch { app.learned.learn(glyph.source.bits.rotate(reading.rotation), letter) }
        updated.savedId?.let { id -> viewModelScope.launch { history.update(id, updated.result) } }
    }

    fun forgetLearned() {
        viewModelScope.launch { app.learned.forget() }
    }

    fun save() {
        val current = _state.value as? ReaderUiState.Result ?: return
        if (current.savedId != null) return
        viewModelScope.launch {
            val id = history.save(current.image, current.result)
            // o usuário pode ter corrigido letras enquanto salvava
            val now = _state.value as? ReaderUiState.Result ?: return@launch
            if (now.image === current.image) {
                _state.value = now.copy(savedId = id)
                if (now.result != current.result) history.update(id, now.result)
            }
        }
    }

    fun openHistory() {
        _state.value = ReaderUiState.History
    }

    fun openSaved(id: Long) = work {
        val saved = history.load(id) ?: throw IllegalStateException("Leitura $id não encontrada")
        ReaderUiState.Result(saved.image, saved.result, saved.id)
    }

    /** Apaga a leitura aberta e volta para o histórico. */
    fun deleteCurrent() {
        val id = (_state.value as? ReaderUiState.Result)?.savedId ?: return
        viewModelScope.launch {
            history.delete(id)
            _state.value = ReaderUiState.History
        }
    }

    fun openSettings() {
        _state.value = ReaderUiState.Settings
    }

    fun setDirection(direction: ReadingDirection) {
        viewModelScope.launch { app.settings.setDirection(direction) }
    }

    fun setMode(mode: ReadingMode) {
        viewModelScope.launch { app.settings.setMode(mode) }
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { app.settings.setTheme(theme) }
    }

    fun goHome() {
        _state.value = ReaderUiState.Home
    }

    private fun work(block: suspend () -> ReaderUiState) {
        _state.value = ReaderUiState.Reading
        viewModelScope.launch {
            _state.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReaderUiState.CouldNotOpen
            }
        }
    }
}
