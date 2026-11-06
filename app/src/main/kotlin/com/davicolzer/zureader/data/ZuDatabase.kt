package com.davicolzer.zureader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Uma leitura salva. [data] guarda a leitura completa no formato de `ReadingCodec`. */
@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val text: String,
    /** Nome do arquivo da imagem, dentro da pasta do app. */
    val imageFile: String,
    val data: String,
)

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE id = :id")
    suspend fun get(id: Long): ReadingEntity?

    @Insert
    suspend fun insert(reading: ReadingEntity): Long

    @Query("UPDATE readings SET text = :text, data = :data WHERE id = :id")
    suspend fun update(id: Long, text: String, data: String)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ReadingEntity::class], version = 1, exportSchema = true)
abstract class ZuDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao

    companion object {
        fun create(context: Context): ZuDatabase =
            Room.databaseBuilder(context, ZuDatabase::class.java, "zu-reader.db").build()
    }
}
