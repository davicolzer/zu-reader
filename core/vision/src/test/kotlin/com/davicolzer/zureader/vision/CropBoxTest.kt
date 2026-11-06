package com.davicolzer.zureader.vision

import com.davicolzer.zureader.vision.CropBox.Handle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CropBoxTest {
    private val box = CropBox(100f, 100f, 300f, 200f)

    @Test
    fun `toque pega canto, borda, dentro ou fora`() {
        assertEquals(Handle.TOP_LEFT, box.hit(105f, 95f, 20f))
        assertEquals(Handle.BOTTOM_RIGHT, box.hit(310f, 210f, 20f))
        assertEquals(Handle.LEFT, box.hit(95f, 150f, 20f))
        assertEquals(Handle.BOTTOM, box.hit(200f, 205f, 20f))
        assertEquals(Handle.MOVE, box.hit(200f, 150f, 20f))
        assertEquals(Handle.NEW, box.hit(500f, 400f, 20f))
    }

    @Test
    fun `puxar um canto mantém as quinas retas`() {
        val moved = box.drag(Handle.TOP_RIGHT, 50f, -30f, 1000, 1000)
        assertEquals(CropBox(100f, 70f, 350f, 200f), moved)
        // o retângulo continua retângulo: os quatro cantos dividem só dois x e dois y
        val q = moved.toQuad()
        assertEquals(q.topLeft.y, q.topRight.y); assertEquals(q.topRight.x, q.bottomRight.x)
        assertEquals(q.bottomRight.y, q.bottomLeft.y); assertEquals(q.bottomLeft.x, q.topLeft.x)
    }

    @Test
    fun `canto que passa do outro vira o retângulo`() {
        assertEquals(CropBox(300f, 100f, 350f, 200f), box.drag(Handle.LEFT, 250f, 0f, 1000, 1000))
    }

    @Test
    fun `arrastar o retângulo não sai da imagem`() {
        assertEquals(CropBox(0f, 0f, 200f, 100f), box.drag(Handle.MOVE, -500f, -500f, 1000, 1000))
        assertEquals(CropBox(800f, 900f, 1000f, 1000f), box.drag(Handle.MOVE, 5000f, 5000f, 1000, 1000))
    }

    @Test
    fun `retângulo novo entre dois pontos, preso à imagem`() {
        assertEquals(CropBox(50f, 0f, 400f, 300f), CropBox.spanning(Point(400f, -20f), Point(50f, 300f), 1000, 1000))
        assertEquals(CropBox(10f, 10f, 40f, 40f), CropBox.spanning(Point(10f, 10f), Point(12f, 11f), 1000, 1000, minSize = 30f))
    }

    @Test
    fun `sugestão torta vira o retângulo que a contém`() {
        val quad = Quad(Point(110f, 90f), Point(300f, 100f), Point(310f, 220f), Point(90f, 200f))
        assertEquals(CropBox(90f, 90f, 310f, 220f), CropBox.around(quad, 1000, 1000))
    }
}
