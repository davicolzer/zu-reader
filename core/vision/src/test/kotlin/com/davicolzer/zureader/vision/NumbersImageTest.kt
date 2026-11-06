package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuNumerals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fase 8: números do Zu em imagens. */
class NumbersImageTest {

    private val numbers = ZuImageReader.forMode(ReadingMode.NUMBERS)

    private fun render(text: String, blockPx: Int = 8) = ZuRenderer.render(text, maxPerLine = 4, blockPx = blockPx, glyphSet = ZuNumerals)

    @Test
    fun `imagem limpa`() {
        val r = numbers.read(render("3#7 12 0"))
        assertEquals("3#7 12 0", r.reading.text)
        assertEquals(ReadingMode.NUMBERS, r.mode)
        assertEquals("3 10 7 / 1 2 / 0", r.mode.display(r.reading.text))
    }

    @Test
    fun `borrao, ruido e JPEG`() {
        val img = ImageEffects.jpeg(ImageEffects.noise(ImageEffects.boxBlur(render("2468 1357", blockPx = 10), 1), 12.0), 0.6f)
        assertEquals("2468 1357", numbers.read(img).reading.text)
    }

    @Test
    fun `imagem colorida`() {
        val gray = render("90#")
        val rgb = RgbImage(gray.width, gray.height, IntArray(gray.pixels.size) {
            if (gray.pixels[it] < 128) (0xFF shl 24) or (90 shl 16) or (40 shl 8) or 140 else (0xFF shl 24) or (200 shl 16) or (170 shl 8) or 240
        })
        assertEquals("90#", numbers.read(rgb).reading.text)
    }

    @Test
    fun `modo escrita nao devolve numeros`() {
        val text = ZuImageReader.forMode(ReadingMode.TEXT).read(render("1234")).reading.text
        assertTrue(text.none { it.isDigit() || it == '#' }, "lido '$text'")
    }

    @Test
    fun `numeros salvos guardam o modo`() {
        val r = numbers.read(render("42"))
        val back = ReadingCodec.decode(ReadingCodec.encode(r))
        assertEquals(ReadingMode.NUMBERS, back.mode)
        assertEquals("42", back.reading.text)
    }

    @Test
    fun `leitura salva antes do modo numeros abre como escrita`() {
        val old = ReadingCodec.encode(ZuImageReader().read(ZuRenderer.render("LOOK"))).lines().toMutableList()
        old[1] = old[1].substringBeforeLast(';') // tira o campo do modo
        assertEquals(ReadingMode.TEXT, ReadingCodec.decode(old.joinToString("\n")).mode)
    }
}
