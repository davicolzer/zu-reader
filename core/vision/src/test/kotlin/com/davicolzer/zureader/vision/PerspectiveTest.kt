package com.davicolzer.zureader.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PerspectiveTest {

    private val reader = ZuImageReader()

    /** Projeta [text] dentro de [quad] num quadro [w]×[h], como uma foto tirada de lado. */
    private fun photographAt(text: GrayImage, quad: Quad, w: Int, h: Int, paper: Int = 210): GrayImage {
        val rect = listOf(Point(0f, 0f), Point(text.width.toFloat(), 0f), Point(text.width.toFloat(), text.height.toFloat()), Point(0f, text.height.toFloat()))
        val m = Perspective.homography(quad.corners, rect) // foto → texto
        return GrayImage(w, h, IntArray(w * h) { i ->
            val x = i % w + 0.5; val y = i / w + 0.5
            val d = m[6] * x + m[7] * y + m[8]
            val sx = ((m[0] * x + m[1] * y + m[2]) / d).toInt()
            val sy = ((m[3] * x + m[4] * y + m[5]) / d).toInt()
            if (sx in 0 until text.width && sy in 0 until text.height) text[sx, sy] else paper
        })
    }

    private val trapezoid = Quad(Point(140f, 60f), Point(560f, 120f), Point(540f, 560f), Point(170f, 650f))

    @Test
    fun `homografia leva cada canto ao seu par`() {
        val from = listOf(Point(0f, 0f), Point(100f, 0f), Point(100f, 50f), Point(0f, 50f))
        val m = Perspective.homography(from, trapezoid.corners)
        for ((p, q) in from.zip(trapezoid.corners)) {
            val d = m[6] * p.x + m[7] * p.y + m[8]
            assertEquals(q.x.toDouble(), (m[0] * p.x + m[1] * p.y + m[2]) / d, 1e-6)
            assertEquals(q.y.toDouble(), (m[3] * p.x + m[4] * p.y + m[5]) / d, 1e-6)
        }
    }

    @Test
    fun `recorte reto devolve a mesma imagem`() {
        val img = ZuRenderer.render("LOOK UP")
        val out = Perspective.rectify(img, Quad.inset(img.width, img.height, 0f))
        assertEquals(img.width, out.width)
        assertEquals(img.height, out.height)
        val diff = img.pixels.indices.count { abs(img.pixels[it] - out.pixels[it]) > 2 }
        assertEquals(0, diff)
    }

    @Test
    fun `foto de lado em trapezio e endireitada pelos cantos`() {
        for (s in listOf("OPEN THE DOOR", "HAVE YOU SEEN THE OWL", "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG")) {
            val photo = photographAt(ZuRenderer.render(s, blockPx = 10, paper = 210), trapezoid, 700, 720)
            val fixed = Perspective.rectify(photo, trapezoid)
            assertEquals(s, reader.read(fixed).reading.text, "frase: $s")
        }
    }

    @Test
    fun `sem endireitar o trapezio forte nao e lido`() {
        val s = "HAVE YOU SEEN THE OWL"
        val photo = photographAt(ZuRenderer.render(s, blockPx = 10, paper = 210), trapezoid, 700, 720)
        assertNotEquals(s, reader.read(photo).reading.text)
    }

    @Test
    fun `cantos marcados com alguns pixels de erro ainda funcionam`() {
        val s = "OPEN THE DOOR"
        val photo = photographAt(ZuRenderer.render(s, blockPx = 10, paper = 210), trapezoid, 700, 720)
        val sloppy = Quad(Point(132f, 52f), Point(566f, 114f), Point(548f, 566f), Point(164f, 658f))
        assertEquals(s, reader.read(Perspective.rectify(photo, sloppy)).reading.text)
    }

    @Test
    fun `recorte sugerido envolve o texto`() {
        val text = ZuRenderer.render("LOOK UP", blockPx = 10, marginBlocks = 20)
        val crop = requireNotNull(reader.read(text).suggestedCrop()) { "sem sugestão de recorte" }
        // o texto começa a 200 px da borda; a sugestão fica entre a borda e o texto
        assertTrue(crop.topLeft.x in 100f..200f, "esquerda ${crop.topLeft.x}")
        assertTrue(crop.topLeft.y in 100f..200f, "topo ${crop.topLeft.y}")
        assertEquals("LOOK UP", reader.read(Perspective.rectify(text, crop)).reading.text)
    }
}
