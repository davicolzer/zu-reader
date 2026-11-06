package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fase 8: números do Zu. */
class ZuNumeralsTest {

    private val classifier = GlyphClassifier(alphabet = ZuNumerals.glyphs)
    private val reader = ZuReader(classifier, languageModel = false)

    @Test
    fun `16 desenhos para os numeros de 0 a 10`() {
        assertEquals(16, ZuNumerals.glyphs.size)
        assertEquals(16, ZuNumerals.glyphs.map { it.pattern }.distinct().size)
        assertEquals((('0'..'9') + '#').toSet(), ZuNumerals.glyphs.map { it.primary }.toSet())
        // variantes: 3, 4, 5, 6 e 7 têm dois desenhos
        assertEquals(listOf('3', '4', '5', '6', '7'), ZuNumerals.glyphs.groupBy { it.primary }.filter { it.value.size == 2 }.keys.sorted())
    }

    @Test
    fun `todo numero tem moldura completa`() {
        for (g in ZuNumerals.glyphs) for (i in 0 until 5) {
            assertTrue(g.pattern[0, i] && g.pattern[4, i] && g.pattern[i, 0] && g.pattern[i, 4], "moldura de ${g.letters}")
        }
    }

    @Test
    fun `cada desenho, inclusive as variantes, e reconhecido`() {
        for (g in ZuNumerals.glyphs) {
            val r = classifier.classify(g.pattern)
            assertEquals(g.primary, r.best?.glyph?.primary)
            assertEquals(1f, r.confidence)
        }
    }

    @Test
    fun `le numeros em colunas, sem modelo do ingles`() {
        val reading = reader.read(ZuLayout.compose("3#7 12 0", glyphs = ZuNumerals, maxPerLine = 4), rotation = null)
        assertEquals("3#7 12 0", reading.text)
        assertEquals(0, reading.rotation)
    }

    @Test
    fun `numeros com um bloco errado ainda sao lidos`() {
        val glyphs = ZuLayout.compose("1234 5678", glyphs = ZuNumerals).mapIndexed { i, g ->
            // inverte um bloco do miolo 3×3 em glifos alternados
            if (i % 2 == 0) g.copy(bits = g.bits.toggled(2, 2)) else g
        }
        val text = reader.read(glyphs, rotation = 0).text
        val wrong = "1234 5678".indices.count { text.getOrNull(it) != "1234 5678"[it] }
        assertTrue(wrong <= 2, "lido '$text'")
    }

    @Test
    fun `texto para mostrar`() {
        assertEquals("3 10 7 / 1 2", ReadingMode.NUMBERS.display("3#7 12"))
        assertEquals("1 / 2\n10", ReadingMode.NUMBERS.display("1 2\n#"))
        assertEquals("OPEN THE", ReadingMode.TEXT.display("OPEN THE"))
        assertEquals("10", ReadingMode.NUMBERS.symbolLabel('#'))
        assertEquals(11, ReadingMode.NUMBERS.keyboard.size)
    }

    @Test
    fun `corrigir numero`() {
        val reading = reader.read(ZuLayout.compose("12", glyphs = ZuNumerals), rotation = 0)
        assertEquals("1#", reading.withLetter(1, '#').text)
    }
}
