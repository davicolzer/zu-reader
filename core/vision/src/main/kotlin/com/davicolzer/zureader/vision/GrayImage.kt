package com.davicolzer.zureader.vision

/** Imagem em tons de cinza (0 = preto, 255 = branco), linha por linha. */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0) { "Imagem vazia" }
        require(pixels.size == width * height) { "Esperava ${width * height} pixels, recebi ${pixels.size}" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

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
