package com.davicolzer.zureader.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * O arquivo de backup da coleção: um .zip com o banco de dados (itens, nomes, descrições,
 * leituras, desenhos e correções aprendidas), a pasta com as fotos e vídeos e um `backup.txt`
 * com a versão do formato e do banco.
 */
object BackupArchive {
    const val FORMAT = 1
    private const val MANIFEST = "backup.txt"
    private const val DATABASE = "database/zu-reader.db"
    private const val FILES = "files/"

    data class Manifest(val format: Int, val dbVersion: Int, val createdAt: Long, val items: Int)

    /** Grava o backup em [out]: o banco [database] e todos os arquivos de [files]. */
    fun pack(database: File, files: File, manifest: Manifest, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                "format=${manifest.format}\ndbVersion=${manifest.dbVersion}\ncreatedAt=${manifest.createdAt}\nitems=${manifest.items}\n".toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATABASE))
            database.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            files.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { f ->
                zip.putNextEntry(ZipEntry(FILES + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Abre o backup de [input] em [target] (`database/` e `files/`) e devolve o manifesto.
     * Recusa arquivos que não são backup do app e caminhos que saem da pasta.
     */
    fun unpack(input: InputStream, target: File): Manifest {
        target.mkdirs()
        val root = target.canonicalFile
        var manifest: Manifest? = null
        var hasDatabase = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                when {
                    entry.name == MANIFEST -> manifest = parse(zip.readBytes().decodeToString())
                    entry.name == DATABASE || entry.name.startsWith(FILES) -> {
                        val out = File(root, entry.name).canonicalFile
                        require(out.path.startsWith(root.path + File.separator)) { "Caminho inválido no backup: ${entry.name}" }
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                        if (entry.name == DATABASE) hasDatabase = true
                    }
                }
            }
        }
        val m = requireNotNull(manifest) { "Não é um backup do Zu Reader" }
        require(hasDatabase) { "Backup sem a coleção" }
        require(m.format <= FORMAT) { "Backup de uma versão mais nova do app" }
        return m
    }

    fun database(unpacked: File) = File(unpacked, DATABASE)

    /** `true` se [file] começa como um banco SQLite (o cabeçalho "SQLite format 3"). */
    fun isSqlite(file: File): Boolean {
        val header = "SQLite format 3\u0000".toByteArray()
        if (file.length() < header.size) return false
        val start = file.inputStream().use { input -> ByteArray(header.size).also { input.read(it) } }
        return start.contentEquals(header)
    }

    fun files(unpacked: File) = File(unpacked, FILES)

    private fun parse(text: String): Manifest {
        val values = text.lines().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        return Manifest(
            format = values["format"]?.toIntOrNull() ?: error("Backup sem versão"),
            dbVersion = values["dbVersion"]?.toIntOrNull() ?: 0,
            createdAt = values["createdAt"]?.toLongOrNull() ?: 0,
            items = values["items"]?.toIntOrNull() ?: 0,
        )
    }
}
