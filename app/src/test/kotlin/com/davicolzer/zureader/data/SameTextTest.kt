package com.davicolzer.zureader.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SameTextTest {
    @Test
    fun `mesmo texto com espacos, quebras e K-Q U-V diferentes`() {
        assertEquals(1.0, SameText.match("THE QUICK BROWN FOX", "THE\nKUICK BROWN FOX"))
        assertEquals(1.0, SameText.match("JUMPS OVER THE LAZY DOG", "JUMPS OUER THE LAZY DOG"))
    }

    @Test
    fun `texto longo aceita uma letra lida diferente`() {
        // 2 letras diferentes em 61: 97%
        val saved = "THE POINTS CONSTELLATE CONSPIRE TO CREATE SHAPES SHAPES TO TESSELLATE"
        assertEquals(true, (SameText.match("THE POINTS CONSTELLATE CONSPIRE TO CR ATE SHAPES SHAPES TO TESSELATE", saved) ?: 0.0) > 0.9)
        // texto bem diferente não conta
        assertNull(SameText.match("THE QUICK BROWN FOX", "JUMPS OVER THE LAZY DOG"))
        // lixo a mais (W W W P) em 23 letras: 84%, não conta
        assertNull(SameText.match("JUMPS OVER THE LAZY DOG W W W P", "JUMPS OVER THE LAZY DOG"))
    }

    @Test
    fun `texto curto precisa ser igual`() {
        assertEquals(1.0, SameText.match("ZU", "zu"))
        assertNull(SameText.match("ZU", "NU"))
        assertNull(SameText.match("WORK", "WORD"))
        assertNull(SameText.match("", "ZU"))
    }

    @Test
    fun `escolhe o salvo mais parecido e, no empate, o primeiro`() {
        val saved = listOf(1L to "GOOD WORK", 2L to "ZU GOOD WORK", 3L to "ZU GOOD WORK")
        assertEquals(2L, SameText.best("ZU GOOD WORK", saved) { it.second }?.first)
        assertNull(SameText.best("NICE TO MEET YOU", saved) { it.second })
    }
}
