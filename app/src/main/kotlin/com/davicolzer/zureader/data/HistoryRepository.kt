package com.davicolzer.zureader.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.davicolzer.zureader.vision.ImageReading
import com.davicolzer.zureader.vision.ReadingCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Item da lista do histórico: uma leitura ou, com [isNote], uma foto anotada ([text] = nome). */
data class HistoryItem(val id: Long, val text: String, val createdAt: Long, val image: File, val isNote: Boolean = false)

/** Foto anotada salva. */
data class SavedNote(val id: Long, val image: Bitmap, val title: String)

/** Leitura salva, pronta para abrir na tela de resultado. */
data class SavedReading(val id: Long, val image: Bitmap, val result: ImageReading)

/** Leituras salvas: dados no banco e imagens em arquivos na pasta do app. */
class HistoryRepository(private val dao: ReadingDao, filesDir: File) {
    private val imagesDir = File(filesDir, "readings").apply { mkdirs() }

    fun recent(limit: Int = Int.MAX_VALUE): Flow<List<HistoryItem>> =
        dao.observeRecent(limit).map { rows ->
            rows.map { HistoryItem(it.id, it.text, it.createdAt, File(imagesDir, it.imageFile), isNote = it.kind == ReadingEntity.KIND_NOTE) }
        }

    /** `true` se o item [id] é uma foto anotada. */
    suspend fun isNote(id: Long): Boolean = withContext(Dispatchers.IO) { dao.get(id)?.kind == ReadingEntity.KIND_NOTE }

    /** Guarda uma foto anotada (a imagem já com os desenhos) com o nome [title]. */
    suspend fun saveNote(image: Bitmap, title: String): Long = withContext(Dispatchers.IO) {
        val name = writeImage(image)
        dao.insert(ReadingEntity(createdAt = System.currentTimeMillis(), text = title, imageFile = name, data = "", kind = ReadingEntity.KIND_NOTE))
    }

    /** Troca a imagem e o nome de uma foto anotada (ao anotar mais). */
    suspend fun updateNote(id: Long, image: Bitmap, title: String) = withContext(Dispatchers.IO) {
        val old = dao.get(id) ?: return@withContext
        val name = writeImage(image)
        dao.updateNote(id, title, name)
        File(imagesDir, old.imageFile).delete()
    }

    suspend fun loadNote(id: Long): SavedNote? = withContext(Dispatchers.IO) {
        val row = dao.get(id)?.takeIf { it.kind == ReadingEntity.KIND_NOTE } ?: return@withContext null
        val image = BitmapFactory.decodeFile(File(imagesDir, row.imageFile).path) ?: return@withContext null
        SavedNote(row.id, image, row.text)
    }

    private fun writeImage(image: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        File(imagesDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return name
    }

    suspend fun save(image: Bitmap, result: ImageReading): Long = withContext(Dispatchers.IO) {
        val name = writeImage(image)
        dao.insert(
            ReadingEntity(
                createdAt = System.currentTimeMillis(),
                text = result.reading.text,
                imageFile = name,
                data = ReadingCodec.encode(result),
            ),
        )
    }

    suspend fun update(id: Long, result: ImageReading) = withContext(Dispatchers.IO) {
        dao.update(id, result.reading.text, ReadingCodec.encode(result))
    }

    suspend fun load(id: Long): SavedReading? = withContext(Dispatchers.IO) {
        val row = dao.get(id) ?: return@withContext null
        val image = BitmapFactory.decodeFile(File(imagesDir, row.imageFile).path) ?: return@withContext null
        SavedReading(row.id, image, ReadingCodec.decode(row.data))
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { File(imagesDir, it.imageFile).delete() }
        dao.delete(id)
    }
}
