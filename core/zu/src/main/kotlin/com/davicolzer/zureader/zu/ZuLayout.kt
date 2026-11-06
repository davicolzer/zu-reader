package com.davicolzer.zureader.zu

import kotlin.math.abs

/**
 * Glifo encontrado na imagem: centro, largura e altura em pixels, e o desenho normalizado.
 * O eixo Y cresce para baixo, como em imagens. Largura e altura podem ser diferentes
 * quando a imagem está esticada ou a foto foi tirada de lado.
 */
data class PlacedGlyph(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val bits: BitMatrix,
) {
    /** Glifo quadrado de lado [size]. */
    constructor(x: Float, y: Float, size: Float, bits: BitMatrix) : this(x, y, size, size, bits)

    val size: Float get() = maxOf(width, height)
}

/** Item da sequência de leitura: um glifo (índice na lista original) ou um espaço. */
sealed interface LayoutToken {
    data class Glyph(val index: Int) : LayoutToken
    data object Space : LayoutToken
}

/**
 * Ordenação dos glifos em linhas/colunas e o inverso (montar a disposição de um texto).
 *
 * Suposições a validar com prints reais (Fase 2):
 * - cada coluna (ou linha) termina uma palavra;
 * - dentro de uma coluna, um vão maior que [WORD_GAP] lados de glifo também separa palavras.
 */
object ZuLayout {
    /** Distância entre centros, em lados de glifo, a partir da qual há um espaço. */
    const val WORD_GAP = 1.75f

    /** Distância entre centros de glifos vizinhos usada por [compose], em lados de glifo. */
    const val PITCH = 1.2f

    fun order(glyphs: List<PlacedGlyph>, direction: ReadingDirection): List<LayoutToken> {
        if (glyphs.isEmpty()) return emptyList()
        val width = glyphs.map { it.width }.sorted()[glyphs.size / 2]
        val height = glyphs.map { it.height }.sorted()[glyphs.size / 2]
        val indexed = glyphs.withIndex().toList()

        // "across" separa as linhas/colunas; "along" é a ordem dentro de cada uma.
        val vertical = direction == ReadingDirection.VERTICAL_RIGHT_TO_LEFT
        val across: (PlacedGlyph) -> Float = if (vertical) { g -> -g.x } else { g -> g.y }
        val along: (PlacedGlyph) -> Float = if (vertical) { g -> g.y } else { g -> g.x }
        val acrossSize = if (vertical) width else height
        val alongSize = if (vertical) height else width

        val lines = mutableListOf<MutableList<IndexedValue<PlacedGlyph>>>()
        for (item in indexed.sortedBy { across(it.value) }) {
            val line = lines.lastOrNull()
            if (line != null && abs(across(item.value) - line.map { across(it.value) }.average()) <= acrossSize / 2) {
                line += item
            } else {
                lines += mutableListOf(item)
            }
        }

        val tokens = mutableListOf<LayoutToken>()
        for (line in lines) {
            if (tokens.isNotEmpty()) tokens += LayoutToken.Space
            var previous: Float? = null
            for (item in line.sortedBy { along(it.value) }) {
                val pos = along(item.value)
                if (previous != null && pos - previous > WORD_GAP * alongSize) tokens += LayoutToken.Space
                tokens += LayoutToken.Glyph(item.index)
                previous = pos
            }
        }
        return tokens
    }

    /**
     * Monta a disposição de [text] em Zu (para testes e dados sintéticos).
     * Quebra em colunas/linhas de até [maxPerLine] glifos sem cortar palavras.
     * Letras fora de A–Z são ignoradas.
     */
    fun compose(
        text: String,
        direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
        maxPerLine: Int = 8,
        size: Float = 10f,
    ): List<PlacedGlyph> {
        val words = text.uppercase().split(Regex("[^A-Z]+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<MutableList<String>>()
        for (word in words) {
            val line = lines.lastOrNull()
            val used = line?.sumOf { it.length + 1 }?.minus(1) ?: 0
            if (line != null && used + 1 + word.length <= maxPerLine) line += word else lines += mutableListOf(word)
        }

        val pitch = PITCH * size
        val out = mutableListOf<PlacedGlyph>()
        lines.forEachIndexed { lineIndex, line ->
            var slot = 0
            line.forEachIndexed { w, word ->
                if (w > 0) slot++ // célula vazia entre palavras
                for (ch in word) {
                    val bits = ZuAlphabet.glyphFor(ch).pattern
                    val (x, y) = when (direction) {
                        ReadingDirection.VERTICAL_RIGHT_TO_LEFT -> -lineIndex * pitch * 1.5f to slot * pitch
                        ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT -> slot * pitch to lineIndex * pitch * 1.5f
                    }
                    out += PlacedGlyph(x, y, size, bits)
                    slot++
                }
            }
        }
        return out
    }

    /**
     * Gira a imagem inteira [quarterTurns] × 90° no sentido horário:
     * posições e desenhos dos glifos.
     */
    fun rotate(glyphs: List<PlacedGlyph>, quarterTurns: Int): List<PlacedGlyph> {
        val q = Math.floorMod(quarterTurns, 4)
        return glyphs.map { g ->
            val (x, y) = when (q) {
                0 -> g.x to g.y
                1 -> -g.y to g.x
                2 -> -g.x to -g.y
                else -> g.y to -g.x
            }
            val (w, h) = if (q % 2 == 0) g.width to g.height else g.height to g.width
            g.copy(x = x, y = y, width = w, height = h, bits = g.bits.rotate(q))
        }
    }
}
