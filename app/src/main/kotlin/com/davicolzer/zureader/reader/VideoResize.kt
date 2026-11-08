package com.davicolzer.zureader.reader

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Vídeos guardados ficam no máximo em **720p** (lado menor até 720 px): maior que isso é
 * convertido (Media3 Transformer) e **só o convertido fica**; 720p ou menor fica como está.
 */
@UnstableApi
object VideoResize {
    const val MAX_SHORT_SIDE = 720

    /** O lado menor do vídeo (px), ou `null` se não deu para ler. */
    fun shortSide(file: File): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (w == null || h == null) null else minOf(w, h)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Devolve o arquivo a guardar: [input] mesmo, se já tem até 720p; senão, a versão em 720p (e
     * [input] é apagado). Se a conversão falhar, fica o original (melhor do que perder o vídeo).
     */
    suspend fun limit(context: Context, input: File): File {
        val side = withContext(Dispatchers.IO) { shortSide(input) } ?: return input
        if (side <= MAX_SHORT_SIDE) return input
        val output = File(input.parentFile, input.nameWithoutExtension + "-720p.mp4")
        val ok = withContext(Dispatchers.Main) { convert(context, input, output) }
        return if (ok && output.length() > 0) {
            input.delete()
            output
        } else {
            output.delete()
            input
        }
    }

    /** O Transformer precisa rodar numa thread com Looper (a principal). */
    private suspend fun convert(context: Context, input: File, output: File): Boolean = suspendCancellableCoroutine { cont ->
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (cont.isActive) cont.resume(false)
                }
            })
            .build()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(MAX_SHORT_SIDE))))
            .build()
        transformer.start(item, output.path)
        cont.invokeOnCancellation { transformer.cancel() }
    }
}
