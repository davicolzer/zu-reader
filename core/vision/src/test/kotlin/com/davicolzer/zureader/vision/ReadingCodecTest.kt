package com.davicolzer.zureader.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReadingCodecTest {

    @Test
    fun `ida e volta preserva a leitura`() {
        val title = ZuRenderer.render("LOOK UP", blockPx = 12)
        val body = ZuRenderer.render("THE TREE OF LIFE", blockPx = 6)
        val original = ZuImageReader().read(ImageEffects.rotate(ImageEffects.stack(listOf(title, body), 24, 230), 5.0, 230))
        val corrected = original.copy(reading = original.reading.withLetter(0, 'B'))

        val back = ReadingCodec.decode(ReadingCodec.encode(corrected))

        assertEquals(corrected.reading.text, back.reading.text)
        assertEquals("BOOK UP\nTHE TREE OF LIFE", back.reading.text)
        assertEquals(corrected.reading.glyphs, back.reading.glyphs)
        assertEquals(corrected.reading.rotation, back.reading.rotation)
        assertEquals(corrected.skewDegrees, back.skewDegrees)
        assertEquals(corrected.width, back.width)
        assertEquals(corrected.height, back.height)
        assertEquals(corrected.inkIsDark, back.inkIsDark)
    }

    @Test
    fun `leitura vazia`() {
        val empty = ZuImageReader().read(GrayImage.filled(50, 50, 200))
        assertEquals("", ReadingCodec.decode(ReadingCodec.encode(empty)).reading.text)
    }

    @Test
    fun `formato desconhecido e recusado`() {
        assertThrows<IllegalArgumentException> { ReadingCodec.decode("outra coisa") }
    }
}
