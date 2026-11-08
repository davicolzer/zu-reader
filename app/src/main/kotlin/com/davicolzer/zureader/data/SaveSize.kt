package com.davicolzer.zureader.data

/**
 * Tamanho máximo das fotos guardadas na coleção: HD (1280 x 720, em pé ou deitada). Maior que
 * isso é reduzido mantendo a proporção; HD ou menor fica como está.
 */
object SaveSize {
    const val HD_LONG = 1280
    const val HD_SHORT = 720

    /** Fator de redução (1 = não muda) para uma foto [width] x [height] caber em HD. */
    fun scale(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 1f
        val long = maxOf(width, height).toFloat()
        val short = minOf(width, height).toFloat()
        return minOf(1f, HD_LONG / long, HD_SHORT / short)
    }

    /** Tamanho final (largura, altura) da foto [width] x [height] depois de caber em HD. */
    fun fit(width: Int, height: Int): Pair<Int, Int> {
        val s = scale(width, height)
        if (s >= 1f) return width to height
        return maxOf(1, Math.round(width * s)) to maxOf(1, Math.round(height * s))
    }
}
