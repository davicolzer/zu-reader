package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.GlyphReading
import com.davicolzer.zureader.zu.PlacedGlyph
import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuLayout
import com.davicolzer.zureader.zu.ZuMixed
import com.davicolzer.zureader.zu.ZuReading
import kotlin.math.roundToInt

/**
 * "Escrever à mão": o texto que o usuário monta tocando nos glifos vira a imagem em Zu e uma
 * leitura exata (as letras escolhidas pelo usuário, sem passar pelo leitor nem pelo modelo do
 * inglês), para abrir na tela de resultado como uma foto lida.
 */
object ZuWriter {
    /** Glifos por coluna (ou linha, na leitura horizontal). */
    const val MAX_PER_LINE = 6

    private const val BLOCK_PX = 10
    private const val MARGIN_BLOCKS = 4

    /**
     * Só letras, números (o 10 é `#`), um espaço entre palavras e `\n` onde o usuário quebrou a
     * coluna (ou linha), em maiúsculas. Quebras no começo ou no fim não contam.
     */
    fun normalize(text: String): String =
        text.uppercase().split('\n')
            .map { line -> line.filter { ZuMixed.contains(it) || it.isWhitespace() }.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") }
            .joinToString("\n")
            .trim('\n')

    /** Escrita, números ou os dois, pelo que o texto tem. */
    fun modeOf(text: String): ReadingMode {
        val hasLetters = text.any { it in 'A'..'Z' }
        val hasNumbers = text.any(ReadingMode::isNumber)
        return when {
            hasLetters && hasNumbers -> ReadingMode.MIXED
            hasNumbers -> ReadingMode.NUMBERS
            else -> ReadingMode.TEXT
        }
    }

    /** Posições dos glifos para desenhar a mensagem (centros em blocos de 1 unidade). */
    fun layout(text: String, direction: ReadingDirection): List<PlacedGlyph> =
        ZuLayout.compose(normalize(text), direction, MAX_PER_LINE, 5f, ZuMixed)

    /** Imagem em Zu e a leitura exata de [text]. `null` se não há nenhum glifo. */
    fun write(text: String, direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT): Pair<GrayImage, ImageReading>? {
        val clean = normalize(text)
        if (clean.isEmpty()) return null
        val image = ZuRenderer.render(clean, direction, MAX_PER_LINE, BLOCK_PX, marginBlocks = MARGIN_BLOCKS, glyphSet = ZuMixed)
        // mesmas posições do ZuRenderer, em pixels da imagem
        val size = 5f * BLOCK_PX
        val placed = ZuLayout.compose(clean, direction, MAX_PER_LINE, size, ZuMixed)
        val margin = MARGIN_BLOCKS * BLOCK_PX
        val minX = placed.minOf { it.x - size / 2 }
        val minY = placed.minOf { it.y - size / 2 }
        val symbols = clean.filter { !it.isWhitespace() }
        check(symbols.length == placed.size) { "Glifos e símbolos não batem" }
        val glyphs = placed.mapIndexed { i, g ->
            val left = (g.x - size / 2 - minX).roundToInt() + margin
            val top = (g.y - size / 2 - minY).roundToInt() + margin
            GlyphReading(PlacedGlyph(left + size / 2, top + size / 2, size, g.bits), symbols[i], confidence = 1f, alternatives = emptyList())
        }
        val reading = ZuReading(clean, glyphs, rotation = 0, direction = direction, score = 0)
        return image to ImageReading(reading, listOf(reading), inkIsDark = true, width = image.width, height = image.height, mode = modeOf(clean))
    }
}
