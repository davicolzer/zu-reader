package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.BitMatrix
import com.davicolzer.zureader.zu.ZuAlphabet

/** Grade lida e quão nítida ela é (0..1). */
data class SampledGlyph(val bits: BitMatrix, val crispness: Float)

/** Lê a grade 5×5 de um glifo olhando o miolo de cada célula. */
object GlyphSampler {
    /** Fração da célula, em cada lado, ignorada para fugir das bordas borradas. */
    private const val MARGIN = 0.25f

    /** Abaixo disto as células ficam "meio tinta, meio fundo": é ruído, não glifo. */
    const val MIN_CRISPNESS = 0.5f

    fun sample(mask: BooleanArray, width: Int, box: PixelBox, grid: Int = ZuAlphabet.GRID): BitMatrix =
        sampleWithQuality(mask, width, box, grid).bits

    /**
     * Nitidez = média, nas células, de |2 × fração de tinta − 1|.
     * Num glifo de verdade o miolo de cada célula é quase todo tinta ou quase todo fundo (≈ 1).
     */
    fun sampleWithQuality(mask: BooleanArray, width: Int, box: PixelBox, grid: Int = ZuAlphabet.GRID): SampledGlyph {
        val cellW = box.width / grid.toFloat()
        val cellH = box.height / grid.toFloat()
        var crisp = 0f
        val bits = BitMatrix.of(grid) { r, c ->
            val x0 = (box.left + (c + MARGIN) * cellW).toInt()
            val x1 = (box.left + (c + 1 - MARGIN) * cellW).toInt().coerceAtLeast(x0 + 1)
            val y0 = (box.top + (r + MARGIN) * cellH).toInt()
            val y1 = (box.top + (r + 1 - MARGIN) * cellH).toInt().coerceAtLeast(y0 + 1)
            var ink = 0
            var total = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                total++
                if (mask[y * width + x]) ink++
            }
            crisp += kotlin.math.abs(2f * ink / total - 1f)
            ink * 2 > total
        }
        return SampledGlyph(bits, crisp / (grid * grid))
    }
}
