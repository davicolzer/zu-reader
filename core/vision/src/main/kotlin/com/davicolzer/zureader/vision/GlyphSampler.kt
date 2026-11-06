package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.BitMatrix
import com.davicolzer.zureader.zu.ZuAlphabet

/** Lê a grade 5×5 de um glifo olhando o miolo de cada célula. */
object GlyphSampler {
    /** Fração da célula, em cada lado, ignorada para fugir das bordas borradas. */
    private const val MARGIN = 0.25f

    fun sample(mask: BooleanArray, width: Int, box: PixelBox, grid: Int = ZuAlphabet.GRID): BitMatrix {
        val cellW = box.width / grid.toFloat()
        val cellH = box.height / grid.toFloat()
        return BitMatrix.of(grid) { r, c ->
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
            ink * 2 > total
        }
    }
}
