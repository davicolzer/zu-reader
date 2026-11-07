package com.davicolzer.zureader.vision

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/** O conteúdo de um QR code: o texto e, se for um endereço da web, o link para abrir. */
data class QrCode(val text: String) {
    /** O link (http/https), se o texto for um; `null` se for outro texto (ex.: um comando do jogo). */
    val link: String? get() = text.trim().takeIf { LINK.matches(it) }

    private companion object {
        val LINK = Regex("(?i)^https?://\\S+$")
    }
}

/**
 * Lê um QR code na imagem (sem internet), com a biblioteca ZXing. Testa também a imagem com as
 * cores invertidas (QR claro sobre fundo escuro, comum em telas e jogos) e reduzida (fotos grandes).
 */
object QrReader {
    fun read(image: RgbImage): QrCode? = read(image.luma())

    fun read(image: GrayImage): QrCode? {
        val versions = buildList {
            add(image)
            add(image.inverted())
            // QR pequeno numa foto grande: tenta também numa versão reduzida
            if (maxOf(image.width, image.height) > SMALL_SIDE) {
                val small = image.scaled(SMALL_SIDE.toFloat() / maxOf(image.width, image.height))
                add(small)
                add(small.inverted())
            }
        }
        for (version in versions) decode(version)?.let { return QrCode(it) }
        return null
    }

    private fun decode(image: GrayImage): String? {
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8",
        )
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(GrayLuminance(image))), hints).text
        } catch (_: NotFoundException) {
            null
        } catch (_: ReaderException) {
            null
        }
    }

    private const val SMALL_SIDE = 800

    private fun GrayImage.scaled(factor: Float): GrayImage {
        val w = (width * factor).toInt().coerceAtLeast(1)
        val h = (height * factor).toInt().coerceAtLeast(1)
        return GrayImage(w, h, IntArray(w * h) { i -> this[((i % w) / factor).toInt().coerceAtMost(width - 1), ((i / w) / factor).toInt().coerceAtMost(height - 1)] })
    }

    /** Ponte entre [GrayImage] e o formato que o ZXing lê. */
    private class GrayLuminance(private val image: GrayImage) : LuminanceSource(image.width, image.height) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = if (row != null && row.size >= width) row else ByteArray(width)
            for (x in 0 until width) out[x] = image[x, y].coerceIn(0, 255).toByte()
            return out
        }

        override fun getMatrix(): ByteArray = ByteArray(width * height) { image.pixels[it].coerceIn(0, 255).toByte() }
    }
}
