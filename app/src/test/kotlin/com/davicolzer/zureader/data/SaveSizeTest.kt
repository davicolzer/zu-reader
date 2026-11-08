package com.davicolzer.zureader.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SaveSizeTest {
    @Test
    fun `foto maior que HD reduz mantendo a proporcao`() {
        assertEquals(1280 to 720, SaveSize.fit(1920, 1080))
        assertEquals(720 to 1280, SaveSize.fit(1080, 1920))
        assertEquals(1280 to 720, SaveSize.fit(4096, 2304))
        // 4:3 deitada: o lado menor (720) é o limite
        assertEquals(960 to 720, SaveSize.fit(1600, 1200))
        assertEquals(720 to 960, SaveSize.fit(1200, 1600))
        // muito larga: o lado maior (1280) é o limite
        assertEquals(1280 to 427, SaveSize.fit(3000, 1000))
    }

    @Test
    fun `HD ou menor fica como esta`() {
        assertEquals(1280 to 720, SaveSize.fit(1280, 720))
        assertEquals(720 to 1280, SaveSize.fit(720, 1280))
        assertEquals(800 to 600, SaveSize.fit(800, 600))
        assertEquals(420 to 360, SaveSize.fit(420, 360))
        assertEquals(1f, SaveSize.scale(1000, 700))
    }
}
