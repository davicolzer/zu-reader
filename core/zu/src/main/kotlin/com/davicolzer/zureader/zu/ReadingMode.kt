package com.davicolzer.zureader.zu

/** Conjunto de glifos que o leitor procura. */
interface GlyphSet {
    val glyphs: List<ZuGlyph>
    fun glyphFor(symbol: Char): ZuGlyph
    fun contains(symbol: Char): Boolean
}

/** Letras e números juntos (ex.: "ROOM 12"): os números são os únicos glifos com moldura completa. */
object ZuMixed : GlyphSet {
    override val glyphs: List<ZuGlyph> = ZuAlphabet.glyphs + ZuNumerals.glyphs
    override fun glyphFor(symbol: Char): ZuGlyph = if (symbol in 'A'..'Z') ZuAlphabet.glyphFor(symbol) else ZuNumerals.glyphFor(symbol)
    override fun contains(symbol: Char): Boolean = ZuAlphabet.contains(symbol) || ZuNumerals.contains(symbol)
}

/** O que está sendo lido: a escrita (letras), os números do Zu, ou os dois misturados. */
enum class ReadingMode(val glyphSet: GlyphSet, val usesLanguageModel: Boolean) {
    TEXT(ZuAlphabet, usesLanguageModel = true),
    NUMBERS(ZuNumerals, usesLanguageModel = false),

    /** Letras e números na mesma imagem ou frase; o modelo do inglês vale entre as letras. */
    MIXED(ZuMixed, usesLanguageModel = true);

    /** Texto para mostrar: na escrita, como foi lido; nos números, "10" no lugar de `#` e cada número separado. */
    fun display(text: String): String = when (this) {
        TEXT -> text
        MIXED -> text.map(::symbolLabel).joinToString("")
        NUMBERS -> text.lines().joinToString("\n") { line ->
            line.split(' ').filter { it.isNotEmpty() }.joinToString(" / ") { word ->
                word.map(::symbolLabel).joinToString(" ")
            }
        }
    }

    /** Como mostrar um símbolo sozinho (ex.: num botão do teclado de correção). */
    fun symbolLabel(symbol: Char): String = if (this != TEXT && symbol == TEN) "10" else symbol.toString()

    /** Símbolos que o usuário pode escolher ao corrigir, na ordem do teclado. */
    val keyboard: List<Char>
        get() = when (this) {
            TEXT -> ('A'..'Z').toList()
            NUMBERS -> ('0'..'9').toList() + TEN + EQUALS
            MIXED -> ('A'..'Z').toList() + ('0'..'9').toList() + TEN + EQUALS
        }

    companion object {
        /** Símbolo usado para o número 10. */
        const val TEN = '#'

        /** Sinal de igual das placas de contas. */
        const val EQUALS = '='

        /** Modo de um glifo pelo símbolo lido (para leituras salvas). */
        fun of(symbol: Char): ReadingMode = if (symbol in 'A'..'Z') TEXT else NUMBERS

        /** `true` para os símbolos de número (0–9 e o 10). */
        fun isNumber(symbol: Char): Boolean = symbol in '0'..'9' || symbol == TEN || symbol == EQUALS
    }
}
