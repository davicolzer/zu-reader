package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZuAlphabetTest {

    @Test
    fun `24 glifos cobrem as 26 letras`() {
        assertEquals(24, ZuAlphabet.glyphs.size)
        val letters = ZuAlphabet.glyphs.flatMap { it.letters.toList() }
        assertEquals(('A'..'Z').toList(), letters.sorted())
    }

    @Test
    fun `K-Q e U-V compartilham glifo`() {
        assertEquals(ZuAlphabet.glyphFor('K'), ZuAlphabet.glyphFor('Q'))
        assertEquals(ZuAlphabet.glyphFor('U'), ZuAlphabet.glyphFor('V'))
    }

    @Test
    fun `todos os desenhos sao diferentes`() {
        assertEquals(24, ZuAlphabet.glyphs.map { it.pattern }.distinct().size)
    }

    @Test
    fun `o alfabeto e formado por 6 formas em 4 rotacoes`() {
        val patterns = ZuAlphabet.glyphs.associateBy { it.pattern }
        val families = ZuAlphabet.glyphs
            .map { g -> g.pattern.rotations().map { patterns.getValue(it).primary }.toSet() }
            .toSet()
        assertEquals(6, families.size)
        families.forEach { assertEquals(4, it.size) }
        // exemplo: A, G, M e S são a mesma forma girada
        assert(setOf('A', 'G', 'M', 'S') in families)
    }

    @Test
    fun `glyphFor aceita minusculas`() {
        assertEquals(ZuAlphabet.glyphFor('A'), ZuAlphabet.glyphFor('a'))
    }
}
