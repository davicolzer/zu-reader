package com.davicolzer.zureader.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ErodeTest {
    @Test
    fun `erosao quebra ponte fina entre dois blocos`() {
        // dois quadrados 5×5 ligados por uma ponte de 1 px de altura
        val w = 13; val h = 7
        val mask = BooleanArray(w * h) { i ->
            val x = i % w; val y = i / w
            (y in 1..5 && (x in 1..5 || x in 7..11)) || (y == 3 && x == 6)
        }
        assertEquals(1, Components.find(mask, w, h).size)
        assertEquals(2, Components.find(Binarizer.erode(mask, w, h, 1), w, h).size)
    }

    @Test
    fun `raio zero nao muda nada`() {
        val mask = booleanArrayOf(true, false, true, true)
        assertEquals(mask.toList(), Binarizer.erode(mask, 2, 2, 0).toList())
    }
}
