package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class ZuReaderTest {

    private val reader = ZuReader()

    private val sentences = listOf(
        "OPEN THE DOOR",
        "LOOK UP",
        "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG",
        "WALK AROUND",
        "VERY QUIET",
        "HAVE YOU SEEN THE OWL",
        "GIVE UP",
        "QUEEN",
        "VIVID",
        "FIVE",
        "NEVER EVER",
        "DEEP WATER",
        "KEY",
        "THE TREE OF LIFE",
        "BOX",
    )

    @Test
    fun `le frases em pe`() {
        for (s in sentences) {
            val reading = reader.read(ZuLayout.compose(s, maxPerLine = 6))
            assertEquals(s, reading.text, "frase: $s")
            assertEquals(0, reading.rotation)
        }
    }

    @Test
    fun `resolve K-Q e U-V pelo contexto`() {
        assertEquals("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG", reader.read(ZuLayout.compose("the quick brown fox jumps over the lazy dog")).text)
        assertEquals("VIVID", reader.read(ZuLayout.compose("VIVID")).text)
        assertEquals("QUEEN", reader.read(ZuLayout.compose("QUEEN")).text)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3])
    fun `detecta foto girada`(turns: Int) {
        // textos com ZuReader.SHORT_TEXT glifos ou mais; os curtos ficam na orientação da foto
        for (s in sentences.filter { it.count(Char::isLetter) >= ZuReader.SHORT_TEXT }) {
            val photo = ZuLayout.rotate(ZuLayout.compose(s, maxPerLine = 6), turns)
            val reading = reader.read(photo)
            assertEquals(s, reading.text, "frase: $s, girada $turns×90°")
            assertEquals((4 - turns) % 4, reading.rotation)
        }
    }

    @Test
    fun `texto curto prefere a foto em pe`() {
        assertEquals("BOX", reader.read(ZuLayout.compose("BOX")).text)
    }

    @Test
    fun `rotacao informada e respeitada`() {
        val photo = ZuLayout.rotate(ZuLayout.compose("OPEN THE DOOR"), 1)
        val reading = reader.read(photo, rotation = 3)
        assertEquals(3, reading.rotation)
        assertEquals("OPEN THE DOOR", reading.text)
    }

    @Test
    fun `leitura horizontal`() {
        val horizontal = ZuReader(direction = ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT)
        val glyphs = ZuLayout.compose("HAVE YOU SEEN THE OWL", ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT, maxPerLine = 9)
        assertEquals("HAVE YOU SEEN THE OWL", horizontal.read(glyphs).text)
    }

    @Test
    fun `glifos voltam na ordem de leitura com a posicao original`() {
        val photo = ZuLayout.rotate(ZuLayout.compose("OPEN THE DOOR", maxPerLine = 4), 1)
        val reading = reader.read(photo)
        assertEquals("OPENTHEDOOR", reading.glyphs.map { it.letter }.joinToString(""))
        assertTrue(reading.glyphs.all { it.source in photo })
        assertTrue(reading.glyphs.none { it.isLowConfidence })
    }

    @Test
    fun `um bloco errado por glifo ainda le o texto`() {
        val random = Random(42)
        var correct = 0
        var total = 0
        repeat(20) {
            for (s in sentences.filter { it.count(Char::isLetter) >= 4 }) {
                val noisy = ZuLayout.compose(s).map { g ->
                    g.copy(bits = g.bits.toggled(random.nextInt(5), random.nextInt(5)))
                }
                val text = reader.read(noisy).text
                total += s.length
                correct += s.indices.count { it < text.length && text[it] == s[it] }
            }
        }
        val accuracy = correct.toDouble() / total
        println("acerto por caractere com 1 bloco errado por glifo: ${"%.1f".format(accuracy * 100)}%")
        assertTrue(accuracy >= 0.95, "acerto por caractere com ruído: ${"%.3f".format(accuracy)}")
    }

    @Test
    fun `glifo com duvida traz alternativas`() {
        // H com um bloco invertido que empata com outro glifo da mesma família
        val h = ZuAlphabet.glyphFor('H').pattern
        val tie = (0 until 25).map { h.toggled(it / 5, it % 5) }
            .first { GlyphClassifier().classify(it).confidence < GlyphClassifier.LOW_CONFIDENCE }
        val glyphs = ZuLayout.compose("THE").toMutableList()
        glyphs[1] = glyphs[1].copy(bits = tie)
        val reading = reader.read(glyphs, rotation = 0)
        val middle = reading.glyphs[1]
        assertTrue(middle.isLowConfidence)
        assertTrue(middle.alternatives.isNotEmpty())
        assertEquals("THE", reading.text)
    }

    @Test
    fun `desenho que nao e glifo vira interrogacao`() {
        val glyphs = ZuLayout.compose("OPEN").toMutableList()
        glyphs[2] = glyphs[2].copy(bits = BitMatrix.parse(".....", ".....", ".....", ".....", "....."))
        val reading = reader.read(glyphs, rotation = 0)
        assertEquals("OP?N", reading.text)
        assertEquals(0f, reading.glyphs[2].confidence)
    }

    @Test
    fun `corrigir uma letra refaz o texto e mantem os espacos`() {
        val reading = reader.read(ZuLayout.compose("OPEN THE DOOR"))
        val fixed = reading.withLetter(4, 'S') // T de THE
        assertEquals("OPEN SHE DOOR", fixed.text)
        assertEquals('S', fixed.glyphs[4].letter)
        assertTrue(fixed.glyphs[4].corrected)
        assertEquals(1f, fixed.glyphs[4].confidence)
        assertEquals(reading.glyphs.size, fixed.glyphs.size)
    }

    @Test
    fun `letra corrigida deixa de ser duvida`() {
        val h = ZuAlphabet.glyphFor('H').pattern
        val tie = (0 until 25).map { h.toggled(it / 5, it % 5) }
            .first { GlyphClassifier().classify(it).confidence < GlyphClassifier.LOW_CONFIDENCE }
        val glyphs = ZuLayout.compose("THE").toMutableList()
        glyphs[1] = glyphs[1].copy(bits = tie)
        val reading = reader.read(glyphs, rotation = 0)
        assertTrue(reading.glyphs[1].isLowConfidence)
        assertTrue(!reading.withLetter(1, 'H').glyphs[1].isLowConfidence)
    }

    @Test
    fun `lista vazia`() {
        assertEquals("", reader.read(emptyList()).text)
    }

    @Test
    fun `texto curto fica na orientacao da foto`() {
        // "ZU" de cabeça para baixo é "IE", que o inglês prefere; com poucos glifos vale a foto
        for (s in listOf("ZU", "NU ZU", "ZU NU", "FEZ", "OWL", "KEY")) {
            val reading = reader.read(ZuLayout.compose(s, maxPerLine = 2))
            assertEquals(s, reading.text, "frase: $s")
            assertEquals(0, reading.rotation)
        }
    }
}
