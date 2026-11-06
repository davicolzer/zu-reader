package com.davicolzer.zureader.vision

/** Imagem em tons de cinza (0 = preto, 255 = branco), linha por linha. */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0) { "Imagem vazia" }
        require(pixels.size == width * height) { "Esperava ${width * height} pixels, recebi ${pixels.size}" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /** Desfoque de caixa de raio [radius] (rápido, por imagem integral). */
    fun boxBlur(radius: Int): GrayImage {
        if (radius <= 0) return this
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var row = 0L
            for (x in 0 until width) {
                row += pixels[y * width + x]
                integral[(y + 1) * (width + 1) + x + 1] = integral[y * (width + 1) + x + 1] + row
            }
        }
        return GrayImage(width, height, IntArray(pixels.size) { i ->
            val x = i % width
            val y = i / width
            val x0 = (x - radius).coerceAtLeast(0)
            val x1 = (x + radius).coerceAtMost(width - 1)
            val y0 = (y - radius).coerceAtLeast(0)
            val y1 = (y + radius).coerceAtMost(height - 1)
            val sum = integral[(y1 + 1) * (width + 1) + x1 + 1] - integral[y0 * (width + 1) + x1 + 1] -
                integral[(y1 + 1) * (width + 1) + x0] + integral[y0 * (width + 1) + x0]
            (sum / ((x1 - x0 + 1) * (y1 - y0 + 1))).toInt()
        })
    }

    /**
     * Estica o contraste: o tom no percentil [low] vira 0 e o do percentil [high] vira 255.
     * Ajuda em fotos lavadas (pouco contraste) ou escuras demais.
     */
    fun stretched(low: Float = 0.01f, high: Float = 0.99f): GrayImage {
        val histogram = IntArray(256)
        for (p in pixels) histogram[p.coerceIn(0, 255)]++
        fun percentile(f: Float): Int {
            val target = (pixels.size * f).toLong()
            var acc = 0L
            for (v in 0..255) { acc += histogram[v]; if (acc > target) return v }
            return 255
        }
        val lo = percentile(low)
        val hi = percentile(high)
        if (hi <= lo) return this
        return GrayImage(width, height, IntArray(pixels.size) { ((pixels[it] - lo) * 255 / (hi - lo)).coerceIn(0, 255) })
    }

    fun inverted(): GrayImage = GrayImage(width, height, IntArray(pixels.size) { 255 - pixels[it] })

    companion object {
        fun filled(width: Int, height: Int, value: Int): GrayImage =
            GrayImage(width, height, IntArray(width * height) { value })

        /**
         * Converte pixels ARGB (como os de um Bitmap do Android) para cinza (luminância Rec. 601).
         * Pixels transparentes viram branco.
         */
        fun fromArgb(width: Int, height: Int, argb: IntArray): GrayImage = GrayImage(width, height, IntArray(argb.size) {
            val p = argb[it]
            val a = (p ushr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (299 * r + 587 * g + 114 * b) / 1000
            (a * luma + (255 - a) * 255) / 255
        })
    }
}
