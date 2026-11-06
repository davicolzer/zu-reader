package com.davicolzer.zureader.vision

/**
 * Converte o plano de luminância (Y) de um quadro da câmera em [GrayImage],
 * já girado para ficar como o usuário vê e reduzido pulando pixels.
 */
object CameraFrames {
    /**
     * @param rotationDegrees 0, 90, 180 ou 270 (sentido horário), como o CameraX informa.
     * @param maxSide lado máximo da saída; a redução pula pixels (rápido, sem filtro).
     */
    fun fromLuma(
        plane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int = 1,
        rotationDegrees: Int = 0,
        maxSide: Int = 640,
    ): GrayImage {
        require(rotationDegrees % 90 == 0) { "Rotação inválida: $rotationDegrees" }
        val step = maxOf(1, (maxOf(width, height) + maxSide - 1) / maxSide)
        val w = width / step
        val h = height / step
        val turns = Math.floorMod(rotationDegrees / 90, 4)
        val outW = if (turns % 2 == 0) w else h
        val outH = if (turns % 2 == 0) h else w
        val out = IntArray(outW * outH)
        for (y in 0 until h) for (x in 0 until w) {
            val v = plane[(y * step) * rowStride + (x * step) * pixelStride].toInt() and 0xFF
            val (ox, oy) = when (turns) {
                0 -> x to y
                1 -> (h - 1 - y) to x
                2 -> (w - 1 - x) to (h - 1 - y)
                else -> y to (w - 1 - x)
            }
            out[oy * outW + ox] = v
        }
        return GrayImage(outW, outH, out)
    }
}

/** Como uma imagem aparece numa área que ela preenche por inteiro, cortando sobras (FILL_CENTER). */
data class FillCenter(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun mapX(x: Float) = x * scale + offsetX
    fun mapY(y: Float) = y * scale + offsetY

    companion object {
        fun of(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): FillCenter {
            val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
            return FillCenter(scale, (viewWidth - imageWidth * scale) / 2, (viewHeight - imageHeight * scale) / 2)
        }
    }
}
