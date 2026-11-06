package com.davicolzer.zureader.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** Versões de cor: glifo e parede que só diferem na cor, ou foto lavada. */
class ColorVariantsTest {

    private val reader = ZuImageReader()

    /** Pinta o texto: tinta com [ink] e fundo com [paper] (ARGB). */
    private fun colorize(gray: GrayImage, ink: Int, paper: Int) =
        RgbImage(gray.width, gray.height, IntArray(gray.pixels.size) { if (gray.pixels[it] < 128) ink else paper })

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `glifo e parede com o mesmo brilho e cores diferentes`() {
        // vermelho (230, 60, 60) e azul-acinzentado (75, 120, 155) têm exatamente o mesmo cinza (110)
        val image = colorize(ZuRenderer.render("OPEN THE DOOR"), ink = rgb(230, 60, 60), paper = rgb(75, 120, 155))
        assertEquals(1, image.luma().pixels.distinct().size)
        assertNotEquals("OPEN THE DOOR", reader.read(image.luma()).reading.text, "em cinza não dá para ler")
        assertEquals("OPEN THE DOOR", reader.read(image).reading.text)
    }

    @Test
    fun `glifo cinza em parede colorida com o mesmo brilho`() {
        // cinza (107) sobre roxo com o mesmo cinza (107): só cor e saturação separam
        val image = colorize(ZuRenderer.render("LOOK UP"), ink = rgb(107, 107, 107), paper = rgb(158, 60, 220))
        assertEquals(1, image.luma().pixels.distinct().size)
        assertEquals("LOOK UP", reader.read(image).reading.text)
    }

    @Test
    fun `foto lavada com pouco contraste`() {
        val washed = ImageEffects.noise(ImageEffects.contrast(ZuRenderer.render("WALK AROUND"), 150, 175), 3.0)
        assertEquals("WALK AROUND", reader.read(RgbImage.fromGray(washed)).reading.text)
    }

    @Test
    fun `leitura colorida de uma imagem boa nao muda`() {
        val image = ZuRenderer.render("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG")
        assertEquals(reader.read(image).reading.text, reader.read(RgbImage.fromGray(image)).reading.text)
    }

    @Test
    fun `recorte mantem as cores`() {
        val image = colorize(ZuRenderer.render("LOOK UP", blockPx = 10, marginBlocks = 10), ink = rgb(220, 60, 60), paper = rgb(40, 140, 110))
        val crop = image.rectify(Quad.inset(image.width, image.height, 0.05f))
        assertEquals("LOOK UP", reader.read(crop).reading.text)
        assertEquals(rgb(40, 140, 110), crop.argb[0])
    }
}
