package com.davicolzer.zureader.zu

/**
 * Os glifos de números do Zu: 0 a 10, mais desenhos alternativos para 3 a 7, e o sinal de igual.
 * Todos são um quadrado com moldura completa e um desenho 3×3 no meio.
 * O 10 usa o símbolo `#` (não existe um caractere para "10"); [ReadingMode.display] mostra "10".
 *
 * Desenhos extraídos da fonte FEZ Pixel Font de TakWolf (CC0 1.0). O alfabeto Zu pertence à Polytron.
 */
object ZuNumerals : GlyphSet {
    override val glyphs: List<ZuGlyph> = listOf(
        glyph("0", "#####", "#...#", "#...#", "#...#", "#####"),
        glyph("1", "#####", "#.#.#", "#.#.#", "#...#", "#####"),
        glyph("2", "#####", "#...#", "#.###", "#...#", "#####"),
        glyph("3", "#####", "#...#", "#.#.#", "#.#.#", "#####"),
        glyph("3", "#####", "#.#.#", "#.###", "#...#", "#####"), // variante
        glyph("4", "#####", "#...#", "###.#", "#...#", "#####"),
        glyph("4", "#####", "#.#.#", "#.#.#", "#.#.#", "#####"), // variante
        glyph("5", "#####", "#.#.#", "###.#", "#...#", "#####"),
        glyph("5", "#####", "#...#", "#.###", "#.#.#", "#####"), // variante
        glyph("6", "#####", "#...#", "#####", "#...#", "#####"),
        glyph("6", "#####", "#.#.#", "#.###", "#.#.#", "#####"), // variante
        glyph("7", "#####", "#...#", "###.#", "#.#.#", "#####"),
        glyph("7", "#####", "#.#.#", "#####", "#...#", "#####"), // variante
        glyph("8", "#####", "#.#.#", "###.#", "#.#.#", "#####"),
        glyph("9", "#####", "#...#", "#####", "#.#.#", "#####"),
        glyph("#", "#####", "#.#.#", "#####", "#.#.#", "#####"),
        // sinal de igual das placas de contas ("1 2 7 = 10"): dois traços, sem moldura
        glyph("=", ".....", "#####", ".....", "#####", "....."),
    )

    private val bySymbol: Map<Char, ZuGlyph> = glyphs.groupBy { it.primary }.mapValues { it.value.first() }

    override fun glyphFor(symbol: Char): ZuGlyph =
        bySymbol[symbol] ?: throw IllegalArgumentException("Símbolo sem glifo de número Zu: '$symbol'")

    override fun contains(symbol: Char): Boolean = symbol in bySymbol

    private fun glyph(symbol: String, vararg rows: String) = ZuGlyph(symbol, BitMatrix.parse(*rows))
}
