package com.davicolzer.zureader.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.ui.theme.ZuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Miniatura de uma imagem salva: decodificada reduzida (no tamanho em que aparece), fora da thread
 * principal, e guardada num cache em memória para a rolagem da coleção não decodificar de novo.
 */
@Composable
fun Thumbnail(file: File, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val target = with(LocalDensity.current) { size.roundToPx() }
    // a foto pode ter sido trocada (anotação editada): a data de modificação entra na chave
    val key = remember(file, target) { "${file.path}|${file.lastModified()}|$target" }
    val bitmap by produceState(ThumbnailCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { decodeThumbnail(file, target) }?.also { ThumbnailCache.put(key, it) }
        }
    }
    Box(modifier.size(size).clip(RoundedCornerShape(10.dp)).background(ZuTheme.colors.ink)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size)) }
    }
}

/** Lê a imagem pulando pixels (potência de 2) até o lado menor ficar perto de [target]. */
private fun decodeThumbnail(file: File, target: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/** Miniaturas já decodificadas (até ~16 MB). */
private object ThumbnailCache : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}
