package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BitMatrixTest {

    private val corner = BitMatrix.parse(
        "##.",
        "#..",
        "...",
    )

    @Test
    fun `rotate90 gira no sentido horario`() {
        // o canto superior esquerdo vai para o canto superior direito
        val expected = BitMatrix.parse(
            ".##",
            "..#",
            "...",
        )
        assertEquals(expected, corner.rotate90())
    }

    @Test
    fun `quatro rotacoes voltam ao original`() {
        assertEquals(corner, corner.rotate90().rotate90().rotate90().rotate90())
        assertEquals(4, corner.rotations().distinct().size)
    }

    @Test
    fun `hamming conta blocos diferentes`() {
        assertEquals(0, corner.hammingDistance(corner))
        assertEquals(6, corner.hammingDistance(corner.rotate90().rotate90()))
        assertNotEquals(corner, corner.rotate90())
    }

    @Test
    fun `parse rejeita matriz nao quadrada`() {
        assertThrows<IllegalArgumentException> { BitMatrix.parse("##", "#") }
    }
}
