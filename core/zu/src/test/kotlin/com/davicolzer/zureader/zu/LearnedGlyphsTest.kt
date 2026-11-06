package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fase 6: aprender com as correções do usuário. */
class LearnedGlyphsTest {

    private val e = ZuAlphabet.glyphFor('E')

    /**
     * Um "E" desenhado de um jeito que o alfabeto padrão lê como outra letra (como uma fonte
     * do jogo um pouco diferente da de referência): começa no E e vai virando blocos na direção
     * da letra mais parecida até ficar mais perto dela.
     */
    private val badE: BitMatrix = run {
        val target = ZuAlphabet.glyphs.filter { it != e }.minBy { it.pattern.hammingDistance(e.pattern) }
        val diff = (0 until 25).filter { e.pattern[it / 5, it % 5] != target.pattern[it / 5, it % 5] }
        var m = e.pattern
        for (cell in diff) {
            m = m.toggled(cell / 5, cell % 5)
            if (m.hammingDistance(target.pattern) < m.hammingDistance(e.pattern)) break
        }
        m
    }

    @Test
    fun `sem exemplo o E deformado e lido errado`() {
        assertNotEquals(e, GlyphClassifier().classify(badE).best?.glyph)
    }

    @Test
    fun `exemplo do usuario vence quando e identico`() {
        val classifier = GlyphClassifier(learned = listOf(ZuGlyph("E", badE)))
        val result = classifier.classify(badE)
        assertEquals("E", result.best?.glyph?.letters)
        assertTrue(result.best!!.learned)
        assertTrue(result.taughtByUser)
        assertEquals(1f, result.confidence)
    }

    @Test
    fun `no empate vale o alfabeto`() {
        // exemplo "errado" igual ao desenho padrão do H: o H continua sendo H
        val h = ZuAlphabet.glyphFor('H')
        val result = GlyphClassifier(learned = listOf(ZuGlyph("X", h.pattern))).classify(h.pattern)
        assertEquals(h, result.best?.glyph)
        assertFalse(result.best!!.learned)
        assertFalse(result.taughtByUser)
    }

    @Test
    fun `exemplos da mesma letra nao tiram confianca`() {
        val classifier = GlyphClassifier(learned = listOf(ZuGlyph("E", e.pattern.toggled(0, 0))))
        assertEquals(1f, classifier.classify(e.pattern).confidence)
    }

    @Test
    fun `um exemplo corrige o mesmo glifo em todo o texto e contra o contexto`() {
        val text = "THE TREE SEES THE DEEP WATER"
        val glyphs = ZuLayout.compose(text).map { g -> if (g.bits == e.pattern) g.copy(bits = badE) else g }

        val before = ZuReader().read(glyphs, rotation = 0).text
        assertNotEquals(text, before)

        val taught = GlyphClassifier(learned = listOf(ZuGlyph("E", badE)))
        assertEquals(text, ZuReader(taught).read(glyphs, rotation = 0).text)
    }

    @Test
    fun `exemplo do usuario funciona com a foto girada`() {
        val text = "THE TREE SEES THE DEEP WATER"
        val glyphs = ZuLayout.compose(text).map { g -> if (g.bits == e.pattern) g.copy(bits = badE) else g }
        val taught = GlyphClassifier(learned = listOf(ZuGlyph("E", badE)))
        val reading = ZuReader(taught).read(ZuLayout.rotate(glyphs, 1))
        assertEquals(3, reading.rotation)
        assertEquals(text, reading.text)
    }
}
