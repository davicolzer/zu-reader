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
import com.davicolzer.zureader.ui.note.Mark
import com.davicolzer.zureader.ui.note.cropMarks
import com.davicolzer.zureader.ui.note.scaled
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

    /**
     * Editando [image] (a foto sem desenhos), começando com [marks]. [note] = a anotação de onde
     * veio (já salva, ou ainda não): voltar abre ela de novo; sem ela, voltar descarta.
     */
    data class Annotate(val image: Bitmap, val marks: List<Mark> = emptyList(), val note: Note? = null) : ReaderUiState

    /** Recortando a foto do editor ([editing] guarda os desenhos para voltar). */
    data class AnnotateCrop(val editing: Annotate) : ReaderUiState

    /** Foto anotada salva. */
    /**
     * A anotação pronta: [image] já desenhada, com o nome, "Editar" e "Salvar". [id] nulo = ainda
     * não salva; [saved] = o que está na tela é o que está no histórico. [original] e [marks]
     * guardam a foto sem desenhos e cada desenho, para editar de novo.
     */
    data class Note(
        val id: Long?,
        val image: Bitmap,
        val title: String,
        val original: Bitmap = image,
        val marks: List<Mark> = emptyList(),
        val saved: Boolean = id != null,
    ) : ReaderUiState
    /** Recorte de uma foto ([fromCamera]) ou de uma imagem da galeria, com os cantos sugeridos. */
    data class Crop(val image: Bitmap, val rgb: RgbImage, val suggestion: Quad, val fromCamera: Boolean) : ReaderUiState
    data object Reading : ReaderUiState

    /**
     * Escrevendo à mão (teclado de glifos); [draft] é o texto já escrito. [from] = a leitura de
     * foto de onde o usuário veio (ao terminar, o texto vale para essa foto e volta para ela).
     */
    data class Write(val draft: String = "", val from: Result? = null) : ReaderUiState

    /** O texto escrito à mão para uma foto, desenhado em Zu (imagem e leitura exata). */
    data class TypedView(val image: Bitmap, val reading: ImageReading)

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
        /** Texto que o usuário escreveu à mão para esta foto, desenhado em Zu ([result].typed). */
        val typed: TypedView? = null,
        /**
         * O texto lido agora já estava na coleção: este é o item salvo, aberto no lugar da leitura
         * nova; [fresh] guarda a leitura nova (para ver mesmo assim) e [savedAt] quando foi salvo.
         */
        val fresh: Result? = null,
        val savedAt: Long? = null,
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
            CameraPurpose.NOTE -> _state.value = editing(photo)
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
            // salvo: só a imagem em HD fica (a original para recortar de novo sai)
            (_state.value as? ReaderUiState.QrResult)?.let {
                if (it.code == code) _state.value = it.copy(savedId = id, image = it.image?.let(ImageLoading::hd), original = null)
            }
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
        editing(withContext(Dispatchers.IO) { ImageLoading.decode(getApplication(), uri) })
    }

    /**
     * "OK" no editor: abre a anotação pronta ([rendered] = a foto com os [marks]), ainda sem salvar,
     * com o nome que já tinha.
     */
    fun finishAnnotating(rendered: Bitmap, marks: List<Mark>) {
        val current = _state.value as? ReaderUiState.Annotate ?: return
        val note = current.note
        _state.value = ReaderUiState.Note(note?.id, rendered, note?.title.orEmpty(), current.image, marks, saved = false)
    }

    /** Salva a anotação aberta com o nome [title] (nova, ou por cima da já salva). */
    fun saveNote(title: String) {
        val note = _state.value as? ReaderUiState.Note ?: return
        viewModelScope.launch {
            val id = note.id?.also { history.updateNote(it, note.original, note.marks, note.image, title) }
                ?: history.saveNote(note.original, note.marks, note.image, title)
            (_state.value as? ReaderUiState.Note)?.takeIf { it.image === note.image }?.let {
                // o editor já trabalha em HD; garante que nada maior fique depois de salvar
                val original = ImageLoading.hd(it.original)
                val factor = original.width / it.original.width.toFloat()
                _state.value = it.copy(
                    id = id,
                    title = title,
                    saved = true,
                    image = ImageLoading.hd(it.image),
                    original = original,
                    marks = if (original === it.original) it.marks else it.marks.map { m -> m.scaled(factor) },
                )
            }
        }
    }

    /** "Editar" na anotação: volta ao editor com os desenhos editáveis. */
    fun editNote() {
        val note = _state.value as? ReaderUiState.Note ?: return
        _state.value = editing(note.original, note.marks, note)
    }

    /**
     * Abre o editor com a foto já em HD (como vai ser salva): editar e salvar usam o mesmo
     * tamanho, e os desenhos acompanham a redução.
     */
    private fun editing(image: Bitmap, marks: List<Mark> = emptyList(), note: ReaderUiState.Note? = null): ReaderUiState.Annotate {
        val hd = ImageLoading.hd(image)
        val factor = hd.width / image.width.toFloat()
        return ReaderUiState.Annotate(hd, if (hd === image) marks else marks.map { it.scaled(factor) }, note)
    }

    /** Sai do editor: volta para a anotação de onde veio ou, se era uma foto nova, para o início. */
    fun leaveAnnotate() {
        val current = _state.value as? ReaderUiState.Annotate ?: return
        _state.value = current.note ?: ReaderUiState.Home
    }

    /** Sai da anotação: salva → histórico; ainda não salva → volta a editar (nada se perde). */
    fun leaveNote() {
        val note = _state.value as? ReaderUiState.Note ?: return
        if (note.id == null) editNote() else _state.value = ReaderUiState.History
    }

    /** "Recortar" no editor: abre o recorte da foto, guardando os desenhos feitos até agora. */
    fun cropAnnotation(marks: List<Mark>) {
        val current = _state.value as? ReaderUiState.Annotate ?: return
        _state.value = ReaderUiState.AnnotateCrop(current.copy(marks = marks))
    }

    /**
     * Recorte escolhido ([quad] nulo = foto inteira): a foto vira só a parte recortada e os desenhos
     * acompanham (os que ficaram totalmente de fora saem).
     */
    fun confirmAnnotationCrop(quad: Quad?) {
        val crop = _state.value as? ReaderUiState.AnnotateCrop ?: return
        val editing = crop.editing
        if (quad == null) {
            _state.value = editing
            return
        }
        work {
            withContext(Dispatchers.Default) {
                val image = editing.image
                val left = quad.corners.minOf { it.x }.toInt().coerceIn(0, image.width - 1)
                val top = quad.corners.minOf { it.y }.toInt().coerceIn(0, image.height - 1)
                val right = quad.corners.maxOf { it.x }.toInt().coerceIn(left + 1, image.width)
                val bottom = quad.corners.maxOf { it.y }.toInt().coerceIn(top + 1, image.height)
                val cut = Bitmap.createBitmap(image, left, top, right - left, bottom - top)
                editing.copy(image = cut, marks = cropMarks(editing.marks, left.toFloat(), top.toFloat(), cut.width, cut.height))
            }
        }
    }

    /** Sai do recorte sem mudar nada. */
    fun leaveAnnotationCrop() {
        val crop = _state.value as? ReaderUiState.AnnotateCrop ?: return
        _state.value = crop.editing
    }

    /** Apaga a foto anotada aberta e volta para o histórico. */
    fun deleteNote() {
        val note = _state.value as? ReaderUiState.Note ?: return
        val id = note.id ?: return
        viewModelScope.launch {
            history.delete(id)
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
                orSaved(ReaderUiState.Result(ImageLoading.toBitmap(rectified), readAll(rectified, whole = crop.rgb), crop = crop.copy(suggestion = quad)))
            }
        }
    }

    fun useWholePhoto() {
        val crop = _state.value as? ReaderUiState.Crop ?: return
        work {
            withContext(Dispatchers.Default) {
                val whole = Quad.inset(crop.rgb.width, crop.rgb.height, 0f)
                orSaved(ReaderUiState.Result(crop.image, readAll(crop.rgb), crop = crop.copy(suggestion = whole)))
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

    /**
     * Se o texto lido já está na coleção, abre o item salvo no lugar da leitura nova (que fica
     * guardada em [ReaderUiState.Result.fresh] para ver mesmo assim).
     */
    private suspend fun orSaved(read: ReaderUiState.Result): ReaderUiState.Result {
        val id = history.findSameText(read.result.reading.text) ?: return read
        val saved = history.load(id) ?: return read
        return ReaderUiState.Result(
            saved.image,
            saved.result,
            saved.id,
            typed = saved.result.typed?.let { typedView(it) },
            fresh = read,
            savedAt = saved.createdAt,
        )
    }

    /**
     * "Ler mesmo assim": mesmo com o texto já na coleção, mostra a leitura feita agora como uma
     * leitura nova (com "Salvar" para guardar como outro item, se quiser).
     */
    fun readAnyway() {
        val fresh = (_state.value as? ReaderUiState.Result)?.fresh ?: return
        _state.value = fresh
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
            val id = history.save(current.image, current.result, written = current.written != null)
            // o usuário pode ter corrigido letras enquanto salvava
            val now = _state.value as? ReaderUiState.Result ?: return@launch
            if (now.image === current.image) {
                // lido e salvo: daqui em diante só a foto em HD (a original grande e o recorte para
                // reler saem da memória; as caixas continuam no lugar, a tela usa o tamanho da leitura)
                _state.value = now.copy(savedId = id, image = ImageLoading.hd(now.image), crop = null)
                if (now.result != current.result) history.update(id, now.result)
            }
        }
    }

    /** Tira o glifo [index] da leitura (não era letra); se já está salva, grava. */
    fun removeGlyph(index: Int) {
        val current = _state.value as? ReaderUiState.Result ?: return
        val reading = current.result.reading
        if (index !in reading.glyphs.indices) return
        setReading(current.result.copy(reading = reading.withoutGlyph(index)))
    }

    /** Volta a leitura para [previous] (desfazer a remoção de uma letra). */
    fun restoreReading(previous: ImageReading) = setReading(previous)

    private fun setReading(result: ImageReading) {
        val current = _state.value as? ReaderUiState.Result ?: return
        val updated = current.copy(result = result)
        _state.value = updated
        updated.savedId?.let { id -> viewModelScope.launch { history.update(id, result) } }
    }

    /** Abre "Escrever à mão" com [draft] já escrito. */
    fun openWriter(draft: String = "") {
        _state.value = ReaderUiState.Write(draft)
    }

    /**
     * Na leitura de uma foto: escrever à mão o texto (quando a leitura saiu ruim). O teclado abre
     * com o que já vale para a foto (o escrito antes ou o lido), para só trocar o que veio errado.
     */
    fun writeForPhoto() {
        val current = _state.value as? ReaderUiState.Result ?: return
        if (current.written != null) return
        val draft = current.result.typed ?: ZuWriter.normalize(current.result.reading.text)
        _state.value = ReaderUiState.Write(draft, from = current)
    }

    /** Sai do teclado: volta para a foto de onde veio, ou para o início. */
    fun leaveWriter() {
        val current = _state.value as? ReaderUiState.Write
        _state.value = current?.from ?: ReaderUiState.Home
    }

    /** Termina de escrever: a mensagem vira imagem em Zu e abre na tela de resultado. */
    fun finishWriting(text: String) {
        val from = (_state.value as? ReaderUiState.Write)?.from
        if (from != null) return finishWritingForPhoto(from, text)
        work {
            val (gray, reading) = ZuWriter.write(text, settings.value.direction) ?: return@work ReaderUiState.Write(text)
            ReaderUiState.Result(ImageLoading.toBitmap(RgbImage.fromGray(gray)), reading, written = text)
        }
    }

    /**
     * O texto escrito passa a valer para a foto [from] (continua uma leitura "Ler Zu", com a foto
     * original). Texto vazio = volta a valer o que a foto leu.
     */
    private fun finishWritingForPhoto(from: ReaderUiState.Result, text: String) = work {
        val clean = ZuWriter.normalize(text).takeIf { it.isNotEmpty() }
        val updated = from.copy(result = from.result.copy(typed = clean), typed = clean?.let { typedView(it) })
        updated.savedId?.let { history.update(it, updated.result) }
        updated
    }

    private suspend fun typedView(text: String): ReaderUiState.TypedView? = withContext(Dispatchers.Default) {
        ZuWriter.write(text, settings.value.direction)?.let { (gray, reading) ->
            ReaderUiState.TypedView(ImageLoading.toBitmap(RgbImage.fromGray(gray)), reading)
        }
    }

    fun openHistory() {
        _state.value = ReaderUiState.History
    }

    fun openSaved(id: Long) = work {
        if (history.isNote(id)) {
            val note = history.loadNote(id) ?: throw IllegalStateException("Anotação $id não encontrada")
            return@work ReaderUiState.Note(note.id, note.image, note.title, note.original, note.marks)
        }
        val saved = history.load(id) ?: throw IllegalStateException("Leitura $id não encontrada")
        // QR salvo pela tela do QR: abre nela de novo
        if (saved.result.reading.glyphs.isEmpty() && saved.result.qr != null && saved.result.typed == null) {
            return@work ReaderUiState.QrResult(saved.result.qr, saved.image, saved.id)
        }
        ReaderUiState.Result(saved.image, saved.result, saved.id, typed = saved.result.typed?.let { typedView(it) })
    }

    /** Apaga um item direto da lista do histórico. */
    fun deleteSaved(id: Long) {
        viewModelScope.launch { history.delete(id) }
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

    /** Histórico em mosaico ([grid]) ou em lista; a escolha fica salva. */
    fun setHistoryGrid(grid: Boolean) {
        viewModelScope.launch { app.settings.setHistoryGrid(grid) }
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
