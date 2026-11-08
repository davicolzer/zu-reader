package com.davicolzer.zureader.data.backup

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * O pouco da API do Google Drive (v3, REST) que o backup usa, só na **pasta de dados do app**
 * (`appDataFolder`: escondida, só o Zu Reader enxerga; não aparece no Drive da pessoa). Um único
 * arquivo de backup, substituído a cada novo backup.
 */
class DriveApi(private val accessToken: String) {
    data class Backup(val id: String, val modifiedAt: Long, val size: Long)

    /** O backup guardado no Drive, ou `null` se ainda não há. */
    fun find(): Backup? {
        val query = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val json = request("GET", "$API/files?spaces=appDataFolder&q=$query&orderBy=modifiedTime%20desc&fields=files(id,modifiedTime,size)")
        val files = JSONObject(json).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        val f = files.getJSONObject(0)
        return Backup(
            id = f.getString("id"),
            modifiedAt = runCatching { Instant.parse(f.getString("modifiedTime")).toEpochMilli() }.getOrDefault(0L),
            size = f.optString("size").toLongOrNull() ?: 0L,
        )
    }

    /** Envia [file] como o backup (cria na primeira vez; depois substitui o conteúdo). */
    fun upload(file: File, existing: Backup?) {
        if (existing != null) {
            // HttpURLConnection não faz PATCH: a API do Google aceita o método no cabeçalho
            request("POST", "$UPLOAD/files/${existing.id}?uploadType=media", contentType = "application/zip", extraHeaders = mapOf("X-HTTP-Method-Override" to "PATCH")) { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            return
        }
        val boundary = "zu-reader-${System.nanoTime()}"
        val metadata = JSONObject().put("name", FILE_NAME).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        request("POST", "$UPLOAD/files?uploadType=multipart", contentType = "multipart/related; boundary=$boundary") { out ->
            out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray())
            out.write("--$boundary\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
    }

    /** Baixa o backup [backup] para [target]. */
    fun download(backup: Backup, target: File) {
        val connection = open("GET", "$API/files/${backup.id}?alt=media")
        check(connection)
        connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private fun open(method: String, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

    private fun request(
        method: String,
        url: String,
        contentType: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        body: ((OutputStream) -> Unit)? = null,
    ): String {
        val connection = open(method, url)
        extraHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        if (body != null) {
            connection.doOutput = true
            connection.setChunkedStreamingMode(64 * 1024)
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            connection.outputStream.use(body)
        }
        check(connection)
        return connection.inputStream.use { it.readBytes().decodeToString() }
    }

    private fun check(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            val detail = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
            throw IOException("Google Drive respondeu $code: ${detail.take(300)}")
        }
    }

    companion object {
        const val FILE_NAME = "zu-reader-backup.zip"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

        /** Escopo pedido à conta: só a pasta de dados do app no Drive. */
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
