package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedLettersTest {
    @Test
    fun `escolhe K ou Q e U ou V pelo ingles`() {
        // o teclado manda sempre a primeira letra do glifo (K, U)
        assertEquals("THE QUICK BROWN FOX", SharedLetters.resolve("THE KUICK BROWN FOX"))
        assertEquals("KEY", SharedLetters.resolve("KEY"))
        assertEquals("JUMPS OVER THE LAZY DOG", SharedLetters.resolve("JUMPS OUER THE LAZY DOG"))
        assertEquals("VERY QUIET", SharedLetters.resolve("UERY KUIET"))
        assertEquals("UP", SharedLetters.resolve("UP"))
    }

    @Test
    fun `atualiza enquanto digita`() {
        // com uma letra só ainda não dá para saber; ao chegar o U, o K vira Q
        assertEquals("QU", SharedLetters.resolve("KU"))
        assertEquals("QUI", SharedLetters.resolve("KUI"))
    }

    @Test
    fun `sem letras ambiguas e com numeros fica igual`() {
        assertEquals("ROOM 12", SharedLetters.resolve("ROOM 12"))
        assertEquals("", SharedLetters.resolve(""))
    }
}
