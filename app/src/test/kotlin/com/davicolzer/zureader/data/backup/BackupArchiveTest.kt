package com.davicolzer.zureader.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackupArchiveTest {
    private fun tempDir(): File = Files.createTempDirectory("backup").toFile()

    @Test
    fun `backup vai e volta com o banco e os arquivos`() {
        val src = tempDir()
        val db = File(src, "zu-reader.db").apply { writeBytes(ByteArray(5000) { (it % 251).toByte() }) }
        val files = File(src, "readings").apply { mkdirs() }
        File(files, "a.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(files, "b.mp4").writeBytes(ByteArray(100_000) { 7 })
        val manifest = BackupArchive.Manifest(BackupArchive.FORMAT, 5, 1_794_000_000_000L, 2)
        val out = ByteArrayOutputStream()
        BackupArchive.pack(db, files, manifest, out)

        val target = tempDir()
        val back = BackupArchive.unpack(ByteArrayInputStream(out.toByteArray()), target)
        assertEquals(manifest, back)
        assertArrayEquals(db.readBytes(), BackupArchive.database(target).readBytes())
        assertEquals(setOf("a.jpg", "b.mp4"), BackupArchive.files(target).list()!!.toSet())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(BackupArchive.files(target), "a.jpg").readBytes())
    }

    @Test
    fun `recusa zip que nao e backup, sem banco ou com caminho para fora`() {
        fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { z -> entries.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
        }.toByteArray()
        val manifest = "format=1\ndbVersion=5\ncreatedAt=1\nitems=0\n".toByteArray()
        assertThrows<IllegalArgumentException> { BackupArchive.unpack(ByteArrayInputStream(zip("foto.jpg" to byteArrayOf(1))), tempDir()) }
        assertThrows<IllegalArgumentException> { BackupArchive.unpack(ByteArrayInputStream(zip("backup.txt" to manifest)), tempDir()) }
        assertThrows<IllegalArgumentException> {
            BackupArchive.unpack(ByteArrayInputStream(zip("backup.txt" to manifest, "database/zu-reader.db" to byteArrayOf(1), "files/../../fora.txt" to byteArrayOf(1))), tempDir())
        }
        // backup de um formato mais novo que o app
        assertThrows<IllegalArgumentException> {
            BackupArchive.unpack(ByteArrayInputStream(zip("backup.txt" to "format=99\n".toByteArray(), "database/zu-reader.db" to byteArrayOf(1))), tempDir())
        }
    }

    @Test
    fun `reconhece um banco sqlite pelo cabecalho`() {
        val dir = tempDir()
        val good = File(dir, "a.db").apply { writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(100)) }
        val bad = File(dir, "b.db").apply { writeBytes("nao e banco".toByteArray()) }
        assertEquals(true, BackupArchive.isSqlite(good))
        assertEquals(false, BackupArchive.isSqlite(bad))
    }
}
