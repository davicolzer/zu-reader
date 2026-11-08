package com.davicolzer.zureader.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.davicolzer.zureader.ui.note.Mark
import com.davicolzer.zureader.ui.note.MarkCodec
import com.davicolzer.zureader.ui.note.scaled
import com.davicolzer.zureader.vision.ImageReading
import com.davicolzer.zureader.vision.ReadingCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Item da lista do histórico: uma leitura ou, com [isNote], uma foto anotada ([text] = nome). */
/** O que foi salvo: a função do app que gerou o item (para o selo e o filtro do histórico). */
enum class SavedKind(val key: String) {
    READING(ReadingEntity.KIND_READING),
    WRITTEN(ReadingEntity.KIND_WRITTEN),
    QR(ReadingEntity.KIND_QR),
    NOTE(ReadingEntity.KIND_NOTE),
    VIDEO(ReadingEntity.KIND_VIDEO),
    ;

    companion object {
        fun of(key: String): SavedKind = entries.firstOrNull { it.key == key } ?: READING
    }
}

data class HistoryItem(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val image: File,
    val kind: SavedKind = SavedKind.READING,
    /** Nome e descrição dados pelo usuário (vazios = sem). */
    val name: String = "",
    val description: String = "",
) {
    val isNote: Boolean get() = kind == SavedKind.NOTE
}

/**
 * Foto anotada salva: [image] já com os desenhos (miniatura, compartilhar); [original] sem eles e
 * [marks], para "Anotar mais" poder mover ou apagar cada desenho.
 */
data class SavedNote(val id: Long, val image: Bitmap, val title: String, val original: Bitmap, val marks: List<Mark>, val description: String = "")

/** Leitura salva, pronta para abrir na tela de resultado. */
data class SavedReading(
    val id: Long,
    val image: Bitmap,
    val result: ImageReading,
    val createdAt: Long = 0,
    val name: String = "",
    val description: String = "",
)

/** Vídeo salvo: o arquivo na pasta da coleção, com nome e descrição. */
data class SavedVideo(val id: Long, val file: File, val name: String, val description: String)

private const val VIDEO_DATA = "video1"

/** Leituras salvas: dados no banco e imagens em arquivos na pasta do app. */
class HistoryRepository(private val dao: ReadingDao, filesDir: File) {
    private val imagesDir = File(filesDir, "readings").apply { mkdirs() }

    fun recent(limit: Int = Int.MAX_VALUE): Flow<List<HistoryItem>> =
        dao.observeRecent(limit).map { rows ->
            rows.map {
                HistoryItem(it.id, it.text, it.createdAt, File(imagesDir, it.imageFile), SavedKind.of(it.kind), it.name, it.description)
            }
        }

    /** `true` se o item [id] é uma foto anotada. */
    suspend fun isNote(id: Long): Boolean = withContext(Dispatchers.IO) { dao.get(id)?.kind == ReadingEntity.KIND_NOTE }

    /** O tipo do item [id] (`null` se não existe). */
    suspend fun kindOf(id: Long): SavedKind? = withContext(Dispatchers.IO) { dao.get(id)?.let { SavedKind.of(it.kind) } }

    /**
     * Guarda um vídeo: o arquivo [video] (gravado ou trazido da galeria) é movido para a pasta da
     * coleção; [thumbnail] (o primeiro quadro, em HD) vira a miniatura.
     */
    suspend fun saveVideo(video: File, thumbnail: Bitmap, name: String, description: String): SavedVideo = withContext(Dispatchers.IO) {
        val videoName = "${UUID.randomUUID()}.mp4"
        val target = File(imagesDir, videoName)
        if (!video.renameTo(target)) {
            video.copyTo(target, overwrite = true)
            video.delete()
        }
        val thumb = writeImage(thumbnail)
        val id = dao.insert(
            ReadingEntity(
                createdAt = System.currentTimeMillis(),
                text = "",
                imageFile = thumb,
                data = VIDEO_DATA + "\n" + videoName,
                kind = ReadingEntity.KIND_VIDEO,
                name = name.trim(),
                description = description.trim(),
            ),
        )
        SavedVideo(id, target, name.trim(), description.trim())
    }

    suspend fun loadVideo(id: Long): SavedVideo? = withContext(Dispatchers.IO) {
        val row = dao.get(id)?.takeIf { it.kind == ReadingEntity.KIND_VIDEO } ?: return@withContext null
        val file = videoFile(row) ?: return@withContext null
        SavedVideo(row.id, file, row.name, row.description)
    }

    private fun videoFile(row: ReadingEntity): File? =
        row.data.lines().takeIf { it.size >= 2 && it[0] == VIDEO_DATA }?.let { File(imagesDir, it[1]) }

    /**
     * Guarda uma foto anotada com o nome [title]: a foto já desenhada ([rendered]) e, para editar
     * depois, a [original] e os desenhos ([marks]).
     */
    suspend fun saveNote(original: Bitmap, marks: List<Mark>, rendered: Bitmap, title: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        val originalFile = writeImage(original)
        val name = writeImage(rendered)
        // a original foi reduzida para HD: os desenhos acompanham
        val factor = SaveSize.scale(original.width, original.height)
        dao.insert(
            ReadingEntity(
                createdAt = System.currentTimeMillis(),
                text = title,
                imageFile = name,
                data = MarkCodec.encode(originalFile, marks.map { it.scaled(factor) }),
                kind = ReadingEntity.KIND_NOTE,
                name = title,
                description = description,
            ),
        )
    }

    /** Troca os desenhos, a foto desenhada e o nome de uma foto anotada (ao anotar mais). */
    suspend fun updateNote(id: Long, original: Bitmap, marks: List<Mark>, rendered: Bitmap, title: String, description: String = "") = withContext(Dispatchers.IO) {
        val old = dao.get(id) ?: return@withContext
        // a original pode ter mudado (recorte): grava as duas de novo e apaga as antigas
        val originalFile = writeImage(original)
        val name = writeImage(rendered)
        val factor = SaveSize.scale(original.width, original.height)
        dao.updateNote(id, title, description, name, MarkCodec.encode(originalFile, marks.map { it.scaled(factor) }))
        File(imagesDir, old.imageFile).delete()
        MarkCodec.decode(old.data)?.let { File(imagesDir, it.originalFile).delete() }
    }

    suspend fun loadNote(id: Long): SavedNote? = withContext(Dispatchers.IO) {
        val row = dao.get(id)?.takeIf { it.kind == ReadingEntity.KIND_NOTE } ?: return@withContext null
        val image = BitmapFactory.decodeFile(File(imagesDir, row.imageFile).path) ?: return@withContext null
        val saved = MarkCodec.decode(row.data)
        val original = saved?.let { BitmapFactory.decodeFile(File(imagesDir, it.originalFile).path) }
        if (saved == null || original == null) SavedNote(row.id, image, row.text, image, emptyList(), row.description)
        else SavedNote(row.id, image, row.text, original, saved.marks, row.description)
    }

    /** Texto da lista do histórico: o escrito à mão, o lido em Zu ou, se só houver QR, "QR: …". */
    private fun listText(result: ImageReading): String =
        result.typed ?: result.reading.text.ifBlank { result.qr?.let { "QR: ${it.text}" }.orEmpty() }

    /** Grava a foto na pasta da coleção, no máximo em HD ([SaveSize]); devolve o nome do arquivo. */
    private fun writeImage(image: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        val (w, h) = SaveSize.fit(image.width, image.height)
        val hd = if (w == image.width && h == image.height) image else Bitmap.createScaledBitmap(image, w, h, true)
        File(imagesDir, name).outputStream().use { hd.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (hd !== image) hd.recycle()
        return name
    }

    /** Guarda uma leitura; [written] = veio de "Escrever à mão". Só QR, sem glifos, é do tipo QR. */
    suspend fun save(
        image: Bitmap,
        result: ImageReading,
        written: Boolean = false,
        name: String = "",
        description: String = "",
    ): Long = withContext(Dispatchers.IO) {
        val kind = when {
            written -> SavedKind.WRITTEN
            result.reading.glyphs.isEmpty() && result.qr != null && result.typed == null -> SavedKind.QR
            else -> SavedKind.READING
        }
        val name = writeImage(image)
        dao.insert(
            ReadingEntity(
                createdAt = System.currentTimeMillis(),
                text = listText(result),
                imageFile = name,
                data = ReadingCodec.encode(result),
                kind = kind.key,
                name = name.trim(),
                description = description.trim(),
            ),
        )
    }

    suspend fun update(id: Long, result: ImageReading) = withContext(Dispatchers.IO) {
        dao.update(id, listText(result), ReadingCodec.encode(result))
    }

    suspend fun load(id: Long): SavedReading? = withContext(Dispatchers.IO) {
        val row = dao.get(id) ?: return@withContext null
        val image = BitmapFactory.decodeFile(File(imagesDir, row.imageFile).path) ?: return@withContext null
        SavedReading(row.id, image, ReadingCodec.decode(row.data), row.createdAt, row.name, row.description)
    }

    /** Troca o nome e a descrição de um item salvo. */
    suspend fun updateDetails(id: Long, name: String, description: String) = withContext(Dispatchers.IO) {
        dao.updateDetails(id, name.trim(), description.trim())
    }

    /**
     * Leitura ou escrita já salva com o mesmo texto que [text] ([SameText]); a mais parecida,
     * e a mais nova no empate. `null` se não há.
     */
    suspend fun findSameText(text: String): Long? = withContext(Dispatchers.IO) {
        if (SameText.key(text).isEmpty()) return@withContext null
        val saved = dao.texts(listOf(ReadingEntity.KIND_READING, ReadingEntity.KIND_WRITTEN))
        SameText.best(text, saved) { it.text }?.id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { row ->
            File(imagesDir, row.imageFile).delete()
            if (row.kind == ReadingEntity.KIND_NOTE) MarkCodec.decode(row.data)?.let { File(imagesDir, it.originalFile).delete() }
            if (row.kind == ReadingEntity.KIND_VIDEO) videoFile(row)?.delete()
        }
        dao.delete(id)
    }
}
