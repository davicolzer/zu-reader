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
import com.davicolzer.zureader.vision.ImageTextReader
import com.davicolzer.zureader.vision.ModeChoice
import com.davicolzer.zureader.vision.ZuImageReader
import com.davicolzer.zureader.vision.ZuWriter
import com.davicolzer.zureader.vision.QrReader
import com.davicolzer.zureader.zu.ZuReading
import com.davicolzer.zureader.vision.QrCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/** Para que a câmera foi aberta. */
enum class CameraPurpose { READ, NOTE, QR }

sealed interface ReaderUiState {
    data object Home : ReaderUiState
    /** Câmera: para ler glifos, para anotar uma foto ou para ler um QR code. */
    data class Camera(val purpose: CameraPurpose = CameraPurpose.READ) : ReaderUiState

    /** O que está num QR code; [code] nulo = a imagem não tinha QR. */
    data class QrResult(
        val code: QrCode?,
        /** Foto ou imagem de onde o QR foi lido (já recortada, se o usuário recortou). */
        val image: Bitmap? = null,
        /** Não nulo = QR que está no histórico. */
        val savedId: Long? = null,
        /** Imagem antes do recorte (para recortar de novo). */
        val original: Bitmap? = null,
    ) : ReaderUiState

    /** Recortando a imagem de um QR antes de salvar. */
    data class QrCrop(val result: QrResult, val image: Bitmap) : ReaderUiState

    /** Editor de anotações sobre [image]; [noteId] não nulo = anotando mais uma foto já salva. */
    data class Annotate(val image: Bitmap, val noteId: Long? = null, val title: String = "") : ReaderUiState

    /** Foto anotada salva. */
    data class Note(val id: Long, val image: Bitmap, val title: String) : ReaderUiState
    /** Recorte de uma foto ([fromCamera]) ou de uma imagem da galeria, com os cantos sugeridos. */
    data class Crop(val image: Bitmap, val rgb: RgbImage, val suggestion: Quad, val fromCamera: Boolean) : ReaderUiState
    data object Reading : ReaderUiState

    /** Escrevendo à mão (teclado de glifos); [draft] é o texto já escrito. */
    data class Write(val draft: String = "") : ReaderUiState

    /**
     * [savedId] não nulo = leitura que está no histórico; correções são gravadas nela.
     * [crop] guarda a imagem original e o recorte usado, para o usuário poder ajustar e ler de novo.
     */
    data class Result(
        val image: Bitmap,
        val result: ImageReading,
        val savedId: Long? = null,
        val crop: Crop? = null,
        /** Texto escrito à mão que gerou este resultado (voltar reabre o teclado com ele). */
        val written: String? = null,
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
    private val imageReader: ImageTextReader
        get() {
            // sempre automático: escrita, números e os dois misturados
            val key = ReaderKey(ModeChoice.AUTO, settings.value.direction, learned.value)
            readerCache?.let { (k, reader) -> if (k == key) return reader }
            return ZuImageReader.forChoice(key.mode, key.direction, key.learned).also { readerCache = key to it }
        }

    private data class ReaderKey(val mode: ModeChoice, val direction: ReadingDirection, val learned: List<ZuGlyph>)

    @Volatile
    private var readerCache: Pair<ReaderKey, ImageTextReader>? = null
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
        _state.value = ReaderUiState.Camera()
    }

    /** Foto da câmera: abre o recorte, com os cantos sugeridos em volta do texto. */
    fun onCaptured(photo: Bitmap) {
        when ((_state.value as? ReaderUiState.Camera)?.purpose) {
            CameraPurpose.NOTE -> _state.value = ReaderUiState.Annotate(photo)
            // fotografou em vez de esperar a leitura ao vivo: procura o QR na foto
            CameraPurpose.QR -> work { ReaderUiState.QrResult(withContext(Dispatchers.Default) { QrReader.read(ImageLoading.toRgb(photo)) }, photo) }
            else -> work { prepareCrop(photo, fromCamera = true) }
        }
    }

    /** "Anotar foto" pela câmera. */
    fun openCameraForNote() {
        _state.value = ReaderUiState.Camera(CameraPurpose.NOTE)
    }

    /** "Ler QR code" pela câmera (lê ao vivo, basta apontar). */
    fun openCameraForQr() {
        _state.value = ReaderUiState.Camera(CameraPurpose.QR)
    }

    /** A câmera achou um QR code ao vivo. */
    fun onQrFound(code: QrCode, photo: Bitmap?) {
        if ((_state.value as? ReaderUiState.Camera)?.purpose == CameraPurpose.QR) _state.value = ReaderUiState.QrResult(code, photo)
    }

    /** Abre o recorte da imagem do QR. */
    fun cropQr() {
        val current = _state.value as? ReaderUiState.QrResult ?: return
        val image = current.original ?: current.image ?: return
        _state.value = ReaderUiState.QrCrop(current, image)
    }

    /** Recorte do QR escolhido (ou [quad] nulo = imagem inteira). */
    fun confirmQrCrop(quad: Quad?) {
        val crop = _state.value as? ReaderUiState.QrCrop ?: return
        work {
            withContext(Dispatchers.Default) {
                val image = if (quad == null) crop.image else ImageLoading.toBitmap(ImageLoading.toRgb(crop.image).rectify(quad))
                crop.result.copy(image = image, original = crop.image)
            }
        }
    }

    fun leaveQrCrop() {
        val crop = _state.value as? ReaderUiState.QrCrop ?: return
        _state.value = crop.result
    }

    /** Guarda o QR (texto e imagem) no histórico. */
    fun saveQr() {
        val current = _state.value as? ReaderUiState.QrResult ?: return
        val code = current.code ?: return
        if (current.savedId != null) return
        val image = current.image ?: ImageLoading.toBitmap(RgbImage.fromGray(com.davicolzer.zureader.vision.GrayImage.filled(64, 64, 230)))
        viewModelScope.launch {
            val reading = ImageReading(ZuReading.EMPTY, emptyList(), true, image.width, image.height, qr = code)
            val id = history.save(image, reading)
            (_state.value as? ReaderUiState.QrResult)?.let { if (it.code == code) _state.value = it.copy(savedId = id) }
        }
    }

    /** Apaga o QR aberto do histórico. */
    fun deleteQr() {
        val id = (_state.value as? ReaderUiState.QrResult)?.savedId ?: return
        viewModelScope.launch {
            history.delete(id)
            _state.value = ReaderUiState.History
        }
    }

    /** "Ler QR code" pela galeria. */
    fun readQr(uri: Uri) = work {
        val bitmap = withContext(Dispatchers.IO) { ImageLoading.decode(getApplication(), uri) }
        ReaderUiState.QrResult(withContext(Dispatchers.Default) { QrReader.read(ImageLoading.toRgb(bitmap)) }, bitmap)
    }

    /** "Anotar foto" pela galeria. */
    fun annotate(uri: Uri) = work {
        ReaderUiState.Annotate(withContext(Dispatchers.IO) { ImageLoading.decode(getApplication(), uri) })
    }

    /** Salva a foto anotada (nova, ou por cima da que estava sendo anotada de novo) e abre ela. */
    fun saveNote(image: Bitmap, title: String) {
        val current = _state.value as? ReaderUiState.Annotate ?: return
        viewModelScope.launch {
            val id = current.noteId?.also { history.updateNote(it, image, title) } ?: history.saveNote(image, title)
            _state.value = ReaderUiState.Note(id, image, title)
        }
    }

    /** Anotar mais na foto anotada aberta. */
    fun editNote() {
        val note = _state.value as? ReaderUiState.Note ?: return
        _state.value = ReaderUiState.Annotate(note.image, note.id, note.title)
    }

    /** Sai do editor: volta para a foto salva (se era uma) ou para o início. */
    fun leaveAnnotate() {
        val current = _state.value as? ReaderUiState.Annotate ?: return
        _state.value = if (current.noteId != null) ReaderUiState.Note(current.noteId, current.image, current.title) else ReaderUiState.Home
    }

    /** Apaga a foto anotada aberta e volta para o histórico. */
    fun deleteNote() {
        val note = _state.value as? ReaderUiState.Note ?: return
        viewModelScope.launch {
            history.delete(note.id)
            _state.value = ReaderUiState.History
        }
    }

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
                ReaderUiState.Result(ImageLoading.toBitmap(rectified), readAll(rectified, whole = crop.rgb), crop = crop.copy(suggestion = quad))
            }
        }
    }

    fun useWholePhoto() {
        val crop = _state.value as? ReaderUiState.Crop ?: return
        work {
            withContext(Dispatchers.Default) {
                val whole = Quad.inset(crop.rgb.width, crop.rgb.height, 0f)
                ReaderUiState.Result(crop.image, readAll(crop.rgb), crop = crop.copy(suggestion = whole))
            }
        }
    }

    /**
     * Lê os glifos e, ao mesmo tempo, procura um QR code na imagem; se o recorte deixou o QR de
     * fora, procura também na foto inteira ([whole]).
     */
    private suspend fun readAll(image: RgbImage, whole: RgbImage? = null): ImageReading = coroutineScope {
        val qr = async { QrReader.read(image) ?: whole?.let(QrReader::read) }
        val glyphs = imageReader.read(image)
        glyphs.copy(qr = qr.await())
    }

    /** Volta da leitura para o recorte, com os cantos que foram usados. */
    fun adjustCrop() {
        val crop = (_state.value as? ReaderUiState.Result)?.crop ?: return
        _state.value = crop
    }

    /** Sai do recorte: volta para a câmera se a imagem veio dela, senão para o início. */
    fun leaveCrop() {
        val crop = _state.value as? ReaderUiState.Crop ?: return
        _state.value = if (crop.fromCamera) ReaderUiState.Camera() else ReaderUiState.Home
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

    /** Abre "Escrever à mão" com [draft] já escrito. */
    fun openWriter(draft: String = "") {
        _state.value = ReaderUiState.Write(draft)
    }

    /** Termina de escrever: a mensagem vira imagem em Zu e abre na tela de resultado. */
    fun finishWriting(text: String) = work {
        val (gray, reading) = ZuWriter.write(text, settings.value.direction) ?: return@work ReaderUiState.Write(text)
        ReaderUiState.Result(ImageLoading.toBitmap(RgbImage.fromGray(gray)), reading, written = text)
    }

    fun openHistory() {
        _state.value = ReaderUiState.History
    }

    fun openSaved(id: Long) = work {
        if (history.isNote(id)) {
            val note = history.loadNote(id) ?: throw IllegalStateException("Anotação $id não encontrada")
            return@work ReaderUiState.Note(note.id, note.image, note.title)
        }
        val saved = history.load(id) ?: throw IllegalStateException("Leitura $id não encontrada")
        // QR salvo pela tela do QR: abre nela de novo
        if (saved.result.reading.glyphs.isEmpty() && saved.result.qr != null) {
            return@work ReaderUiState.QrResult(saved.result.qr, saved.image, saved.id)
        }
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
