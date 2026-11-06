package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ReadingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ZuWriterTest {
    @Test
    fun `texto escrito vira imagem e leitura exata`() {
        val (image, result) = ZuWriter.write("the  quick brown fox")!!
        assertEquals("THE QUICK BROWN FOX", result.reading.text)
        assertEquals(16, result.reading.glyphs.size)
        assertEquals(ReadingMode.TEXT, result.mode)
        // a letra escolhida vale, mesmo onde o inglês preferiria a outra (K e Q dividem o glifo)
        assertEquals("KUICK", ZuWriter.write("KUICK")!!.second.reading.text)
        // e o leitor de imagens lê a mesma coisa na imagem gerada
        assertEquals("THE QUICK BROWN FOX", ZuImageReader().read(image).reading.text)
    }

    @Test
    fun `numeros e misto`() {
        assertEquals(ReadingMode.NUMBERS, ZuWriter.write("3 #7")!!.second.mode)
        assertEquals("10", ReadingMode.NUMBERS.symbolLabel('#'))
        val (image, mixed) = ZuWriter.write("ROOM 12", ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT)!!
        assertEquals(ReadingMode.MIXED, mixed.mode)
        assertEquals("ROOM 12", mixed.reading.text)
        assertEquals(ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT, mixed.reading.direction)
        // cada caixa fica em cima do glifo desenhado
        val g = mixed.reading.glyphs.first().source
        assertEquals(image[(g.x - g.width / 2 + 2).toInt(), (g.y - g.height / 2 + 2).toInt()] < 128, g.bits[0, 0])
    }

    @Test
    fun `salva e abre igual`() {
        val (_, result) = ZuWriter.write("OPEN DOOR 3")!!
        assertEquals(result.reading.text, ReadingCodec.decode(ReadingCodec.encode(result)).reading.text)
    }

    @Test
    fun `nada para escrever`() {
        assertNull(ZuWriter.write("  "))
        assertEquals("", ZuWriter.normalize("!? "))
    }
}
