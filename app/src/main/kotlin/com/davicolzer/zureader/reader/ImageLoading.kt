package com.davicolzer.zureader.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import com.davicolzer.zureader.vision.RgbImage
import kotlin.math.max
import kotlin.math.roundToInt

/** Abre imagens da galeria em tamanho controlado e converte para o leitor. */
object ImageLoading {
    /** Lado máximo, em pixels, usado na leitura. */
    const val MAX_SIDE = 1600

    fun decode(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder já aplica a rotação EXIF da foto.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = maxSide / max(info.size.width, info.size.height).toFloat()
                if (scale < 1f) {
                    decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }
            }
        } else {
            decodeLegacy(context, uri, maxSide)
        }

    private fun decodeLegacy(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("Imagem inválida: $uri")
    }

    /** Gira [bitmap] para ficar em pé e reduz para no máximo [maxSide]. */
    fun upright(bitmap: Bitmap, rotationDegrees: Int, maxSide: Int = MAX_SIDE): Bitmap {
        val scale = minOf(1f, maxSide / max(bitmap.width, bitmap.height).toFloat())
        if (rotationDegrees == 0 && scale == 1f) return bitmap
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun toRgb(bitmap: Bitmap): RgbImage {
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return RgbImage(bitmap.width, bitmap.height, argb)
    }

    fun toBitmap(rgb: RgbImage): Bitmap = Bitmap.createBitmap(rgb.argb, rgb.width, rgb.height, Bitmap.Config.ARGB_8888)
}
