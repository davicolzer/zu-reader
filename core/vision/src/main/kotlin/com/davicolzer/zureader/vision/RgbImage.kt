package com.davicolzer.zureader.vision

/**
 * Imagem colorida (ARGB, como um Bitmap do Android). Dela saem as versões em cinza que o
 * leitor testa: luminância, cada canal de cor e saturação.
 */
class RgbImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(width > 0 && height > 0) { "Imagem vazia" }
        require(argb.size == width * height) { "Esperava ${width * height} pixels, recebi ${argb.size}" }
    }

    /** Brilho percebido (Rec. 601). Transparente vira branco. */
    fun luma(): GrayImage = GrayImage.fromArgb(width, height, argb)

    fun red(): GrayImage = channel(16)
    fun green(): GrayImage = channel(8)
    fun blue(): GrayImage = channel(0)

    /** Saturação (0 = cinza, 255 = cor pura): separa glifo "vivo" de parede "apagada" (ou o contrário). */
    fun saturation(): GrayImage = GrayImage(width, height, IntArray(argb.size) { i ->
        val p = argb[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == 0) 0 else (max - min) * 255 / max
    })

    /** Recorta e endireita [quad], mantendo as cores (canal por canal). */
    fun rectify(quad: Quad, maxSide: Int = 1600): RgbImage {
        val r = Perspective.rectify(red(), quad, maxSide)
        val g = Perspective.rectify(green(), quad, maxSide)
        val b = Perspective.rectify(blue(), quad, maxSide)
        return RgbImage(r.width, r.height, IntArray(r.pixels.size) {
            (0xFF shl 24) or (r.pixels[it] shl 16) or (g.pixels[it] shl 8) or b.pixels[it]
        })
    }

    private fun channel(shift: Int): GrayImage = GrayImage(width, height, IntArray(argb.size) { i ->
        val p = argb[i]
        val a = (p ushr 24) and 0xFF
        val c = (p shr shift) and 0xFF
        (a * c + (255 - a) * 255) / 255
    })

    companion object {
        fun fromGray(gray: GrayImage) = RgbImage(gray.width, gray.height, IntArray(gray.pixels.size) {
            val v = gray.pixels[it]
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        })
    }
}
