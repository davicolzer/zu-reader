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

/** Item da lista do histórico. */
data class HistoryItem(val id: Long, val text: String, val createdAt: Long, val image: File)

/** Leitura salva, pronta para abrir na tela de resultado. */
data class SavedReading(val id: Long, val image: Bitmap, val result: ImageReading)

/** Leituras salvas: dados no banco e imagens em arquivos na pasta do app. */
class HistoryRepository(private val dao: ReadingDao, filesDir: File) {
    private val imagesDir = File(filesDir, "readings").apply { mkdirs() }

    fun recent(limit: Int = Int.MAX_VALUE): Flow<List<HistoryItem>> =
        dao.observeRecent(limit).map { rows -> rows.map { HistoryItem(it.id, it.text, it.createdAt, File(imagesDir, it.imageFile)) } }

    suspend fun save(image: Bitmap, result: ImageReading): Long = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        File(imagesDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 92, it) }
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
