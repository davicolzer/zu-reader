package com.davicolzer.zureader.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Um item salvo: uma leitura ([kind] = [KIND_READING], [data] guarda a leitura completa no formato
 * de `ReadingCodec`) ou uma foto anotada ([kind] = [KIND_NOTE], [text] é o nome dado pelo usuário).
 */
@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val text: String,
    /** Nome do arquivo da imagem, dentro da pasta do app. */
    val imageFile: String,
    val data: String,
    @ColumnInfo(defaultValue = KIND_READING) val kind: String = KIND_READING,
) {
    companion object {
        const val KIND_READING = "reading"
        const val KIND_WRITTEN = "written"
        const val KIND_QR = "qr"
        const val KIND_NOTE = "note"
    }
}

/** Só o id e o texto de um item salvo. */
data class SavedText(val id: Long, val text: String)

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReadingEntity>>

    /** Id e texto dos itens de [kinds], do mais novo para o mais antigo (para achar texto repetido). */
    @Query("SELECT id, text FROM readings WHERE kind IN (:kinds) ORDER BY createdAt DESC")
    suspend fun texts(kinds: List<String>): List<SavedText>

    @Query("SELECT * FROM readings WHERE id = :id")
    suspend fun get(id: Long): ReadingEntity?

    @Insert
    suspend fun insert(reading: ReadingEntity): Long

    @Query("UPDATE readings SET text = :text, data = :data WHERE id = :id")
    suspend fun update(id: Long, text: String, data: String)

    @Query("UPDATE readings SET text = :text, imageFile = :imageFile, data = :data WHERE id = :id")
    suspend fun updateNote(id: Long, text: String, imageFile: String, data: String)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Glifo ensinado pelo usuário ao corrigir uma letra: desenho como foi lido (25 caracteres # ou .) → letra. */
@Entity(tableName = "learned_glyphs")
data class LearnedGlyphEntity(
    @PrimaryKey val bits: String,
    val letter: String,
    val createdAt: Long,
)

@Dao
interface LearnedGlyphDao {
    @Query("SELECT * FROM learned_glyphs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LearnedGlyphEntity>>

    @Upsert
    suspend fun upsert(glyph: LearnedGlyphEntity)

    /** Mantém só os [keep] exemplos mais recentes. */
    @Query("DELETE FROM learned_glyphs WHERE bits NOT IN (SELECT bits FROM learned_glyphs ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM learned_glyphs")
    suspend fun clear()
}

@Database(
    entities = [ReadingEntity::class, LearnedGlyphEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class ZuDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
    abstract fun learnedGlyphs(): LearnedGlyphDao

    companion object {
        fun create(context: Context): ZuDatabase =
            Room.databaseBuilder(context, ZuDatabase::class.java, "zu-reader.db").addMigrations(MIGRATION_3_4).build()

        /**
         * 3 → 4: o tipo passa a separar também "escrita" e "QR code". A tabela não muda; os QR
         * já salvos (lista "QR: …") viram do tipo QR. Escritas antigas não dá para distinguir e
         * ficam como leitura.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE readings SET kind = '${ReadingEntity.KIND_QR}' WHERE kind = '${ReadingEntity.KIND_READING}' AND text LIKE 'QR: %'")
            }
        }
    }
}
