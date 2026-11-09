package com.davicolzer.zureader.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CursorEditTest {
    private fun of(text: String) = CursorEdit(text.toList())
    private fun CursorEdit<Char>.text() = items.joinToString("")

    @Test
    fun `digita no fim e no meio, como num texto`() {
        val e = of("ACE").type('F')
        assertEquals("ACEF", e.text())
        val mid = e.moveTo(1).type('B')
        assertEquals("ABCEF", mid.text())
        assertEquals(2, mid.cursor)
    }

    @Test
    fun `apaga antes do cursor e nao passa do comeco`() {
        val e = of("ABC").moveTo(2).delete()
        assertEquals("AC", e.text())
        assertEquals(1, e.cursor)
        assertEquals("AC", e.moveTo(0).delete().text())
    }

    @Test
    fun `selecionar troca ou apaga a peca escolhida`() {
        val chosen = of("ABC").select(1)
        assertEquals(1, chosen.selected)
        val replaced = chosen.type('X')
        assertEquals("AXC", replaced.text())
        assertNull(replaced.selected)
        assertEquals(2, replaced.cursor)
        assertEquals("AC", chosen.delete().text())
        // tocar de novo tira a seleção e deixa o cursor depois do item
        assertEquals(CursorEdit("ABC".toList(), 2), chosen.select(1))
    }

    @Test
    fun `setas movem o cursor e saem da selecao`() {
        val e = of("ABC").moveTo(1)
        assertEquals(0, e.left().cursor)
        assertEquals(0, e.left().left().cursor)
        assertEquals(2, e.right().cursor)
        assertEquals(1, of("ABC").select(1).left().cursor)
        assertEquals(2, of("ABC").select(1).right().cursor)
        assertEquals(3, of("ABC").right().cursor)
    }
}
