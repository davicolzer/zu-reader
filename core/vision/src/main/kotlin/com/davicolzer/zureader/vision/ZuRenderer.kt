package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.GlyphSet
import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ZuAlphabet
import com.davicolzer.zureader.zu.ZuLayout
import kotlin.math.roundToInt

/** Desenha um texto em Zu como imagem (para testes, exemplos e dados sintéticos). */
object ZuRenderer {
    fun render(
        text: String,
        direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
        maxPerLine: Int = 6,
        blockPx: Int = 8,
        ink: Int = 40,
        paper: Int = 230,
        marginBlocks: Int = 4,
        glyphSet: GlyphSet = ZuAlphabet,
    ): GrayImage {
        val size = 5f * blockPx
        val glyphs = ZuLayout.compose(text, direction, maxPerLine, size, glyphSet)
        require(glyphs.isNotEmpty()) { "Texto sem letras" }
        val margin = marginBlocks * blockPx
        val minX = glyphs.minOf { it.x - size / 2 }
        val minY = glyphs.minOf { it.y - size / 2 }
        val width = (glyphs.maxOf { it.x + size / 2 } - minX).roundToInt() + 2 * margin
        val height = (glyphs.maxOf { it.y + size / 2 } - minY).roundToInt() + 2 * margin
        val pixels = IntArray(width * height) { paper }
        for (g in glyphs) {
            val left = (g.x - size / 2 - minX).roundToInt() + margin
            val top = (g.y - size / 2 - minY).roundToInt() + margin
            for (r in 0 until g.bits.size) for (c in 0 until g.bits.size) {
                if (!g.bits[r, c]) continue
                for (y in top + r * blockPx until top + (r + 1) * blockPx)
                    for (x in left + c * blockPx until left + (c + 1) * blockPx)
                        pixels[y * width + x] = ink
            }
        }
        return GrayImage(width, height, pixels)
    }
}
