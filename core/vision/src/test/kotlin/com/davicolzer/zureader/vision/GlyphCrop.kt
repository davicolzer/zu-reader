package com.davicolzer.zureader.vision

/**
 * Entrada da rede de glifos: o recorte em tons de cinza da caixa, reduzido para [SIZE]×[SIZE],
 * com a tinta em 1 e o fundo em 0 (contraste esticado).
 */
object GlyphCrop {
    const val SIZE = 16

    fun extract(image: GrayImage, box: PixelBox, inkIsDark: Boolean): FloatArray {
        val out = FloatArray(SIZE * SIZE)
        val sx = box.width / SIZE.toFloat()
        val sy = box.height / SIZE.toFloat()
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val x = box.left + (c + 0.5f) * sx - 0.5f
            val y = box.top + (r + 0.5f) * sy - 0.5f
            out[r * SIZE + c] = bilinear(image, x, y)
        }
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (v in out) { if (v < lo) lo = v; if (v > hi) hi = v }
        val range = (hi - lo).coerceAtLeast(1f)
        for (i in out.indices) {
            val v = (out[i] - lo) / range
            out[i] = if (inkIsDark) 1f - v else v
        }
        return out
    }

    private fun bilinear(img: GrayImage, x: Float, y: Float): Float {
        val x0 = kotlin.math.floor(x).toInt().coerceIn(0, img.width - 1)
        val y0 = kotlin.math.floor(y).toInt().coerceIn(0, img.height - 1)
        val x1 = (x0 + 1).coerceAtMost(img.width - 1)
        val y1 = (y0 + 1).coerceAtMost(img.height - 1)
        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)
        val top = img[x0, y0] * (1 - fx) + img[x1, y0] * fx
        val bottom = img[x0, y1] * (1 - fx) + img[x1, y1] * fx
        return top * (1 - fy) + bottom * fy
    }
}
