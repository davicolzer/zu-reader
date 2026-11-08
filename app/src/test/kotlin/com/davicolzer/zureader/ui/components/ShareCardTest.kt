package com.davicolzer.zureader.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShareCardTest {
    @Test
    fun `mensagem junta nome, texto e descricao e tira os vazios`() {
        assertEquals("Lápide da floresta\n\nJUMPS OVER THE LAZY DOG\n\nPerto do farol", ShareCard.message("Lápide da floresta", "JUMPS OVER THE LAZY DOG", "Perto do farol"))
        assertEquals("JUMPS OVER THE LAZY DOG", ShareCard.message("  ", "JUMPS OVER THE LAZY DOG", null))
        assertEquals("", ShareCard.message(null, "", " "))
    }
}
