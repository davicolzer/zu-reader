package com.davicolzer.zureader.zu

/** Conjunto de glifos que o leitor procura. */
interface GlyphSet {
    val glyphs: List<ZuGlyph>
    fun glyphFor(symbol: Char): ZuGlyph
    fun contains(symbol: Char): Boolean
}

/** O que está sendo lido: a escrita (letras) ou os números do Zu. */
enum class ReadingMode(val glyphSet: GlyphSet, val usesLanguageModel: Boolean) {
    TEXT(ZuAlphabet, usesLanguageModel = true),
    NUMBERS(ZuNumerals, usesLanguageModel = false);

    /** Texto para mostrar: na escrita, como foi lido; nos números, "10" no lugar de `#` e cada número separado. */
    fun display(text: String): String = when (this) {
        TEXT -> text
        NUMBERS -> text.lines().joinToString("\n") { line ->
            line.split(' ').filter { it.isNotEmpty() }.joinToString(" / ") { word ->
                word.map(::symbolLabel).joinToString(" ")
            }
        }
    }

    /** Como mostrar um símbolo sozinho (ex.: num botão do teclado de correção). */
    fun symbolLabel(symbol: Char): String = if (this == NUMBERS && symbol == TEN) "10" else symbol.toString()

    /** Símbolos que o usuário pode escolher ao corrigir, na ordem do teclado. */
    val keyboard: List<Char>
        get() = when (this) {
            TEXT -> ('A'..'Z').toList()
            NUMBERS -> ('0'..'9').toList() + TEN
        }

    companion object {
        /** Símbolo usado para o número 10. */
        const val TEN = '#'

        /** Modo de um glifo pelo símbolo lido (para leituras salvas). */
        fun of(symbol: Char): ReadingMode = if (symbol in 'A'..'Z') TEXT else NUMBERS
    }
}
