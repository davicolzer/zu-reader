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
