package com.davicolzer.zureader.zu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedLettersTest {
    @Test
    fun `escolhe K ou Q e U ou V pelo ingles`() {
        // o teclado de glifos marca o glifo de K/Q como 'k' e o de U/V como 'u'
        assertEquals("THE QUICK BROWN FOX", SharedLetters.resolve("THE kuICk BROWN FOX"))
        assertEquals("KEY", SharedLetters.resolve("kEY"))
        assertEquals("JUMPS OVER THE LAZY DOG", SharedLetters.resolve("JuMPS OuER THE LAZY DOG"))
        assertEquals("VERY QUIET", SharedLetters.resolve("uERY kuIET"))
        assertEquals("UP", SharedLetters.resolve("uP"))
    }

    @Test
    fun `atualiza enquanto digita`() {
        // com uma letra só ainda não dá para saber; ao chegar o U, o K vira Q
        assertEquals("QU", SharedLetters.resolve("ku"))
        assertEquals("QUI", SharedLetters.resolve("kuI"))
    }

    @Test
    fun `sem letras ambiguas e com numeros fica igual`() {
        assertEquals("ROOM 12", SharedLetters.resolve("ROOM 12"))
        assertEquals("", SharedLetters.resolve(""))
    }

    @Test
    fun `letra escolhida no teclado de letras nao muda`() {
        // maiúsculas vieram do teclado de letras: o usuário quis exatamente isso
        assertEquals("KUICK", SharedLetters.resolve("KUICK"))
        assertEquals("QEY", SharedLetters.resolve("QEY"))
        // misturado: só o glifo tocado é decidido pelo inglês
        assertEquals("QUICK", SharedLetters.resolve("kUICK"))
    }
}
