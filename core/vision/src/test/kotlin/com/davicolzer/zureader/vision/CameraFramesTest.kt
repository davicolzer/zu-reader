package com.davicolzer.zureader.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CameraFramesTest {

    // quadro 3×2 com 1 byte de sobra por linha (rowStride = 4)
    private val plane = byteArrayOf(
        1, 2, 3, 0,
        4, 5, 6, 0,
    )

    @Test
    fun `sem rotacao respeita o rowStride`() {
        val g = CameraFrames.fromLuma(plane, 3, 2, rowStride = 4)
        assertEquals(3, g.width); assertEquals(2, g.height)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), g.pixels.toList())
    }

    @Test
    fun `90 graus vira retrato`() {
        val g = CameraFrames.fromLuma(plane, 3, 2, rowStride = 4, rotationDegrees = 90)
        assertEquals(2, g.width); assertEquals(3, g.height)
        // girar 90° no sentido horário: a coluna da esquerda (1,4) vira a linha de cima (4,1)
        assertEquals(listOf(4, 1, 5, 2, 6, 3), g.pixels.toList())
    }

    @Test
    fun `180 e 270 graus`() {
        assertEquals(listOf(6, 5, 4, 3, 2, 1), CameraFrames.fromLuma(plane, 3, 2, 4, rotationDegrees = 180).pixels.toList())
        assertEquals(listOf(3, 6, 2, 5, 1, 4), CameraFrames.fromLuma(plane, 3, 2, 4, rotationDegrees = 270).pixels.toList())
    }

    @Test
    fun `reduz pulando pixels`() {
        val big = ByteArray(1280 * 960) { (it % 256).toByte() }
        val g = CameraFrames.fromLuma(big, 1280, 960, rowStride = 1280, maxSide = 640)
        assertEquals(640, g.width); assertEquals(480, g.height)
    }

    @Test
    fun `bytes acima de 127 viram valores positivos`() {
        val g = CameraFrames.fromLuma(byteArrayOf(-1, -128), 2, 1, rowStride = 2)
        assertEquals(listOf(255, 128), g.pixels.toList())
    }

    @Test
    fun `fill center corta as sobras e centraliza`() {
        // imagem 4:3 deitada numa tela 1:2 em pé: escala pela altura, sobra na largura
        val m = FillCenter.of(400, 300, viewWidth = 300f, viewHeight = 600f)
        assertEquals(2f, m.scale)
        assertEquals(-250f, m.offsetX)
        assertEquals(0f, m.offsetY)
        assertEquals(150f, m.mapX(200f))
    }
}
