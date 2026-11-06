package com.davicolzer.zureader.zu

/**
 * Um glifo do alfabeto Zu. Alguns glifos representam duas letras (K/Q e U/V):
 * nesses casos o leitor escolhe a letra pelo contexto.
 */
data class ZuGlyph(val letters: String, val pattern: BitMatrix) {
    init {
        require(letters.isNotEmpty() && letters.all { it in 'A'..'Z' }) { "Letras inválidas: $letters" }
    }

    val primary: Char get() = letters[0]
}

/**
 * Os 24 glifos de letras do alfabeto Zu, em grade 5×5, na orientação de leitura.
 *
 * O alfabeto é formado por 6 formas, cada uma em 4 rotações (ex.: A, G, M e S são
 * a mesma forma girada). Por isso a rotação de um glifo muda a letra, e o leitor
 * nunca gira glifos um a um: só o texto inteiro.
 *
 * Padrões extraídos da fonte FEZ Pixel Font de TakWolf (CC0 1.0,
 * https://github.com/TakWolf/fez-pixel-font). O alfabeto Zu pertence à Polytron.
 */
object ZuAlphabet {
    const val GRID = 5

    val glyphs: List<ZuGlyph> = listOf(
        glyph("A", "#####", "..#..", "#...#", "#####", "#####"),
        glyph("B", "#####", "#...#", "#.#.#", "....#", "#####"),
        glyph("C", "#####", "###..", "###.#", "###..", "#####"),
        glyph("D", "#####", "#####", "#####", "..###", "#.###"),
        glyph("E", "#####", "..###", "#.###", "#....", "#####"),
        glyph("F", "#####", "#.#.#", "....#", "#.#.#", "#####"),
        glyph("G", "###.#", "##..#", "##.##", "##..#", "###.#"),
        glyph("H", "#####", "#...#", "#.#.#", "#...#", "###.#"),
        glyph("I", "#.#.#", "#...#", "#####", "#####", "#####"),
        glyph("J", "#####", "#####", "#####", "###..", "###.#"),
        glyph("KQ", "###.#", "###.#", "###.#", "#...#", "#.###"),
        glyph("L", "#####", "#...#", "##.##", "#...#", "##.##"),
        glyph("M", "#####", "#####", "#...#", "..#..", "#####"),
        glyph("N", "#####", "#....", "#.#.#", "#...#", "#####"),
        glyph("O", "#####", "..###", "#.###", "..###", "#####"),
        glyph("P", "###.#", "###..", "#####", "#####", "#####"),
        glyph("R", "#####", "#.#.#", "#....", "#.#.#", "#####"),
        glyph("S", "#.###", "#..##", "##.##", "#..##", "#.###"),
        glyph("T", "#.###", "#...#", "#.#.#", "#...#", "#####"),
        glyph("UV", "#####", "#####", "#####", "#...#", "#.#.#"),
        glyph("W", "#.###", "..###", "#####", "#####", "#####"),
        glyph("X", "###.#", "#...#", "#.###", "#.###", "#.###"),
        glyph("Y", "##.##", "#...#", "##.##", "#...#", "#####"),
        glyph("Z", "#####", "....#", "###.#", "###..", "#####"),
    )

    private val byLetter: Map<Char, ZuGlyph> =
        glyphs.flatMap { g -> g.letters.map { it to g } }.toMap()

    /** Glifo que representa [letter] (A–Z, maiúscula ou minúscula). */
    fun glyphFor(letter: Char): ZuGlyph =
        byLetter[letter.uppercaseChar()] ?: throw IllegalArgumentException("Letra sem glifo Zu: '$letter'")

    private fun glyph(letters: String, vararg rows: String) = ZuGlyph(letters, BitMatrix.parse(*rows))
}
