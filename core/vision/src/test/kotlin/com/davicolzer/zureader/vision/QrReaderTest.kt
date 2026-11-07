package com.davicolzer.zureader.vision

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QrReaderTest {
    /** Desenha um QR code como imagem cinza, com margem, módulos de [module] px. */
    private fun qr(text: String, module: Int = 6, ink: Int = 0, paper: Int = 255, margin: Int = 40): GrayImage {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        val w = m.width * module + 2 * margin
        val h = m.height * module + 2 * margin
        return GrayImage(w, h, IntArray(w * h) { i ->
            val x = (i % w - margin) / module
            val y = (i / w - margin) / module
            if (i % w >= margin && i / w >= margin && x < m.width && y < m.height && m[x, y]) ink else paper
        })
    }

    @Test
    fun `le texto e reconhece link`() {
        val link = QrReader.read(qr("https://example.com/fez?x=1"))!!
        assertEquals("https://example.com/fez?x=1", link.text)
        assertEquals("https://example.com/fez?x=1", link.link)

        // um comando do jogo: só texto, sem link
        val command = QrReader.read(qr("RT RB LT LB A A"))!!
        assertEquals("RT RB LT LB A A", command.text)
        assertNull(command.link)
    }

    @Test
    fun `qr claro sobre fundo escuro, com ruido e pequeno numa imagem grande`() {
        assertEquals("ZU", QrReader.read(qr("ZU", ink = 240, paper = 20))?.text)
        assertEquals("HELLO", QrReader.read(ImageEffects.jpeg(ImageEffects.noise(ImageEffects.boxBlur(qr("HELLO", module = 8), 1), 12.0), 0.6f))?.text)
        // QR pequeno no canto de uma imagem de 1600 px
        val small = qr("CORNER", module = 4)
        val big = GrayImage(1600, 1200, IntArray(1600 * 1200) { i -> val x = i % 1600; val y = i / 1600; if (x < small.width && y < small.height) small[x, y] else 255 })
        assertEquals("CORNER", QrReader.read(big)?.text)
    }

    @Test
    fun `imagem sem qr`() {
        assertNull(QrReader.read(ZuRenderer.render("THE QUICK BROWN FOX")))
    }
}
