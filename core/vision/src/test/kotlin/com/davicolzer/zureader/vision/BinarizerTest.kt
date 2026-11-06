package com.davicolzer.zureader.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BinarizerTest {
    @Test
    fun `otsu separa duas cores`() {
        val img = GrayImage(4, 1, intArrayOf(20, 30, 200, 210))
        val t = Binarizer.otsu(img)
        assertTrue(t in 30 until 200, "limiar $t")
        assertEquals(listOf(true, true, false, false), Binarizer.mask(img, t, inkIsDark = true).toList())
    }

    @Test
    fun `componentes com vizinhanca de 8`() {
        val mask = booleanArrayOf(
            true, false, false,
            false, true, false,
            false, false, true,
        )
        val boxes = Components.find(mask, 3, 3)
        assertEquals(1, boxes.size)
        assertEquals(PixelBox(0, 0, 3, 3, area = 3), boxes.single())
    }
}
