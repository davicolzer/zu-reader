package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZuLayoutTest {

    private fun text(glyphs: List<PlacedGlyph>, direction: ReadingDirection): String {
        val letters = glyphs.map { g -> ZuAlphabet.glyphs.first { it.pattern == g.bits }.primary }
        return ZuLayout.order(glyphs, direction).joinToString("") {
            when (it) {
                LayoutToken.Space -> " "
                is LayoutToken.Glyph -> letters[it.index].toString()
            }
        }
    }

    @Test
    fun `colunas da direita para a esquerda, de cima para baixo`() {
        val glyphs = ZuLayout.compose("OPEN THE DOOR", maxPerLine = 4)
        // 3 colunas; a primeira (OPEN) fica mais à direita
        assertEquals(3, glyphs.map { it.x }.distinct().size)
        assertEquals(glyphs.maxOf { it.x }, glyphs.first().x)
        assertEquals("OPEN THE DOOR", text(glyphs, ReadingDirection.VERTICAL_RIGHT_TO_LEFT))
    }

    @Test
    fun `vao dentro da coluna vira espaco`() {
        val glyphs = ZuLayout.compose("LOOK UP", maxPerLine = 8)
        assertEquals(1, glyphs.map { it.x }.distinct().size)
        assertEquals("LOOK UP", text(glyphs, ReadingDirection.VERTICAL_RIGHT_TO_LEFT))
    }

    @Test
    fun `leitura horizontal`() {
        val glyphs = ZuLayout.compose("WALK AROUND THE TREE", ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT, maxPerLine = 11)
        assertEquals("WALK AROUND THE TREE", text(glyphs, ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT))
    }

    @Test
    fun `ordem nao depende da ordem de entrada`() {
        val glyphs = ZuLayout.compose("OPEN THE DOOR", maxPerLine = 4)
        assertEquals("OPEN THE DOOR", text(glyphs.shuffled(kotlin.random.Random(7)), ReadingDirection.VERTICAL_RIGHT_TO_LEFT))
    }

    @Test
    fun `quatro rotacoes voltam ao original`() {
        val glyphs = ZuLayout.compose("OPEN THE DOOR")
        assertEquals(glyphs, ZuLayout.rotate(ZuLayout.rotate(glyphs, 3), 1))
        assertEquals(glyphs, ZuLayout.rotate(glyphs, 4))
    }
}
