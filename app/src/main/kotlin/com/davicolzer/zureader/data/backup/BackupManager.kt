package com.davicolzer.zureader.data.backup

import android.content.Context
import com.davicolzer.zureader.data.ZuDatabase
import java.io.File

/**
 * Monta o backup da coleção a partir do que está no celular e põe de volta um backup baixado.
 * Restaurar **substitui** a coleção atual (banco e arquivos); depois o app precisa reabrir.
 */
class BackupManager(private val context: Context, private val database: () -> ZuDatabase) {
    private val collection = File(context.filesDir, "readings")
    private val work = File(context.cacheDir, "backup")

    /** Gera o arquivo de backup no cache e devolve. */
    fun create(): File {
        val db = database()
        // grava no arquivo do banco o que ainda está no diário (WAL), para a cópia sair completa
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val items = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM readings").use { if (it.moveToFirst()) it.getInt(0) else 0 }
        work.mkdirs()
        val out = File(work, DriveApi.FILE_NAME)
        out.outputStream().use {
            BackupArchive.pack(
                context.getDatabasePath(DB_NAME),
                collection,
                BackupArchive.Manifest(BackupArchive.FORMAT, ZuDatabase.VERSION, System.currentTimeMillis(), items),
                it,
            )
        }
        return out
    }

    /**
     * Põe no lugar o backup [zip]: confere, fecha o banco e troca o banco e a pasta da coleção.
     * Backup de uma versão mais nova do app é recusado antes de mexer em qualquer coisa.
     */
    fun restore(zip: File): BackupArchive.Manifest {
        val unpacked = File(work, "restore").apply { deleteRecursively() }
        val manifest = zip.inputStream().use { BackupArchive.unpack(it, unpacked) }
        require(manifest.dbVersion <= ZuDatabase.VERSION) { "Backup de uma versão mais nova do app" }
        // confere antes de mexer em qualquer coisa: um arquivo estragado não pode deixar o app sem coleção
        require(BackupArchive.isSqlite(BackupArchive.database(unpacked))) { "Backup com a coleção estragada" }
        database().close()
        val dbFile = context.getDatabasePath(DB_NAME)
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { it.delete() }
        BackupArchive.database(unpacked).copyTo(dbFile, overwrite = true)
        collection.deleteRecursively()
        collection.mkdirs()
        BackupArchive.files(unpacked).listFiles()?.forEach { it.copyTo(File(collection, it.name), overwrite = true) }
        unpacked.deleteRecursively()
        zip.delete()
        return manifest
    }

    companion object {
        const val DB_NAME = "zu-reader.db"
    }
}
