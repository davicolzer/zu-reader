package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlyphClassifierTest {

    private val classifier = GlyphClassifier()

    @Test
    fun `cada glifo exato e reconhecido com confianca total`() {
        for (glyph in ZuAlphabet.glyphs) {
            val result = classifier.classify(glyph.pattern)
            assertEquals(glyph, result.best?.glyph)
            assertEquals(0, result.best?.distance)
            assertEquals(1f, result.confidence)
        }
    }

    @Test
    fun `com um bloco errado o glifo certo continua entre os mais proximos`() {
        var unique = 0
        var ties = 0
        for (glyph in ZuAlphabet.glyphs) for (r in 0 until 5) for (c in 0 until 5) {
            val result = classifier.classify(glyph.pattern.toggled(r, c))
            val nearest = result.matches.filter { it.distance == 1 }.map { it.glyph }
            assertTrue(glyph in nearest, "${glyph.letters} com bloco ($r,$c) invertido")
            if (nearest.size == 1) {
                unique++
                assertEquals(glyph, result.best?.glyph)
                assertEquals(0.8f, result.confidence)
            } else {
                ties++
                assertTrue(result.confidence < GlyphClassifier.LOW_CONFIDENCE, "empate precisa ficar marcado como dúvida")
            }
        }
        assertEquals(24 * 25, unique + ties)
        assertTrue(unique > ties, "a maioria dos erros de 1 bloco não deveria empatar ($unique × $ties)")
    }

    @Test
    fun `desenho vazio nao e glifo`() {
        val empty = BitMatrix.parse(".....", ".....", ".....", ".....", ".....")
        val result = classifier.classify(empty)
        assertFalse(result.isGlyph)
        assertEquals(0f, result.confidence)
    }
}
