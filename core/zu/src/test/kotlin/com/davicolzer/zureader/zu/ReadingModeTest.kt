package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingModeTest {
    @Test
    fun `numero corrigido numa leitura de texto vira leitura misturada`() {
        assertEquals(ReadingMode.MIXED, ReadingMode.TEXT.withSymbol('3'))
        assertEquals(ReadingMode.MIXED, ReadingMode.TEXT.withSymbol(ReadingMode.TEN))
        assertEquals(ReadingMode.TEXT, ReadingMode.TEXT.withSymbol('A'))
    }

    @Test
    fun `letra corrigida numa leitura de numeros vira leitura misturada`() {
        assertEquals(ReadingMode.MIXED, ReadingMode.NUMBERS.withSymbol('K'))
        assertEquals(ReadingMode.NUMBERS, ReadingMode.NUMBERS.withSymbol('7'))
        assertEquals(ReadingMode.MIXED, ReadingMode.MIXED.withSymbol('7'))
    }
}
