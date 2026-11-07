package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ZuReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZuImageReaderTest {

    private val reader = ZuImageReader()

    private val sentences = listOf(
        "OPEN THE DOOR",
        "LOOK UP",
        "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG",
        "WALK AROUND",
        "HAVE YOU SEEN THE OWL",
        "GIVE UP",
        "DEEP WATER",
        "THE TREE OF LIFE",
    )

    /** Lê cada frase depois de [effect] e devolve o acerto por caractere. */
    private fun accuracy(effect: (GrayImage) -> GrayImage, render: (String) -> GrayImage = { ZuRenderer.render(it) }): Double {
        var correct = 0
        var total = 0
        for (s in sentences) {
            val text = reader.read(effect(render(s))).reading.text
            total += s.length
            correct += s.indices.count { it < text.length && text[it] == s[it] }
        }
        return correct.toDouble() / total
    }

    private fun assertAccuracy(name: String, value: Double, minimum: Double = 0.95) {
        println("$name: ${"%.1f".format(value * 100)}%")
        assertTrue(value >= minimum, "$name: ${"%.3f".format(value)} < $minimum")
    }

    @Test
    fun `imagem limpa`() {
        for (s in sentences) assertEquals(s, reader.read(ZuRenderer.render(s)).reading.text)
    }

    @Test
    fun `texto claro sobre fundo escuro`() {
        val result = reader.read(ZuRenderer.render("OPEN THE DOOR", ink = 240, paper = 30))
        assertEquals("OPEN THE DOOR", result.reading.text)
        assertFalse(result.inkIsDark)
    }

    @Test
    fun `glifos encostados viram vaga da grade e sao lidos`() {
        val text = "THE QUICK BROWN FOX"
        val clean = ZuRenderer.render(text, maxPerLine = 6, blockPx = 8)
        val glyphs = reader.read(clean).reading.glyphs.map { it.source }
        val p = clean.pixels.copyOf()
        // liga U–I (QUICK) e R–O (BROWN) com uma barra de tinta no vão, como dois glifos que a
        // foto juntou: a mancha dupla é descartada e sobra uma vaga de 2 glifos no meio da coluna
        var bridges = 0
        for (i in listOf(4, 10)) {
            val a = glyphs[i]
            val b = glyphs[i + 1]
            val top = (a.y + a.height / 2).toInt() - 1
            val bottom = (b.y - b.height / 2).toInt() + 1
            for (y in top..bottom) for (x in (a.x - a.width / 2).toInt() until (a.x + a.width / 2).toInt()) p[y * clean.width + x] = 40
            bridges++
        }
        assertEquals(2, bridges)
        assertEquals(text, reader.read(GrayImage(clean.width, clean.height, p)).reading.text)
    }

    @Test
    fun `glifos pequenos e escala quebrada`() {
        assertAccuracy("bloco 4 px", accuracy({ it }) { ZuRenderer.render(it, blockPx = 4) })
        assertAccuracy("escala 0,66", accuracy({ ImageEffects.scale(it, 0.66) }, { ZuRenderer.render(it, blockPx = 10) }))
        assertAccuracy("escala 1,37", accuracy({ ImageEffects.scale(it, 1.37) }))
    }

    @Test
    fun `borrao, ruido e JPEG`() {
        assertAccuracy("borrão", accuracy({ ImageEffects.boxBlur(it, 1) }))
        assertAccuracy("ruído σ=25", accuracy({ ImageEffects.noise(it, 25.0) }))
        assertAccuracy("JPEG 40%", accuracy({ ImageEffects.jpeg(it, 0.4f) }))
        assertAccuracy(
            "tudo junto",
            accuracy({ ImageEffects.jpeg(ImageEffects.noise(ImageEffects.boxBlur(ImageEffects.scale(it, 0.8), 1), 15.0), 0.6f) }),
        )
    }

    @Test
    fun `baixo contraste e sujeira`() {
        assertAccuracy("contraste 100–160", accuracy({ ImageEffects.contrast(it, 100, 160) }))
        assertAccuracy("sujeira e moldura", accuracy({ ImageEffects.clutter(it, ink = 40) }))
    }

    @Test
    fun `texto no meio de um cenario`() {
        assertAccuracy("cenário", accuracy({ ImageEffects.inScene(it) }), minimum = 0.9)
        assertAccuracy("cenário + JPEG", accuracy({ ImageEffects.jpeg(ImageEffects.inScene(it), 0.6f) }), minimum = 0.9)
    }

    @Test
    fun `foto girada`() {
        val upright = ZuRenderer.render("OPEN THE DOOR")
        var img = upright
        for (turn in 1..3) {
            img = ImageEffects.rotate90(img)
            val result = reader.read(img)
            assertEquals("OPEN THE DOOR", result.reading.text, "girada $turn×90°")
            assertEquals((4 - turn) % 4, result.reading.rotation)
        }
    }

    @Test
    fun `leitura horizontal`() {
        val horizontal = ZuImageReader(ZuReader(direction = ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT))
        val img = ZuRenderer.render("HAVE YOU SEEN THE OWL", ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT, maxPerLine = 9)
        assertEquals("HAVE YOU SEEN THE OWL", horizontal.read(img).reading.text)
    }

    @Test
    fun `posicao dos glifos bate com a imagem`() {
        val img = ZuRenderer.render("LOOK", blockPx = 10, marginBlocks = 4)
        val glyphs = reader.read(img).reading.glyphs
        assertEquals(4, glyphs.size)
        val first = glyphs.first().source
        assertEquals(50f, first.size)
        assertEquals(40f + 25f, first.x)
        assertEquals(40f + 25f, first.y)
    }

    @Test
    fun `quadrado solido do tamanho de um glifo nao vira letra`() {
        val text = ZuRenderer.render("LOOK", blockPx = 8, marginBlocks = 10)
        val p = text.pixels.copyOf()
        // bloco sólido 40×40 à esquerda do texto
        for (y in 10 until 50) for (x in 4 until 44) p[y * text.width + x] = 40
        assertEquals("LOOK", reader.read(GrayImage(text.width, text.height, p)).reading.text)
    }

    @Test
    fun `transparencia vira branco`() {
        val gray = GrayImage.fromArgb(2, 1, intArrayOf(0x00000000, 0xFF000000.toInt()))
        assertEquals(listOf(255, 0), gray.pixels.toList())
    }

    @Test
    fun `imagem sem texto`() {
        assertEquals("", reader.read(GrayImage.filled(200, 120, 200)).reading.text)
        assertEquals("", reader.read(ImageEffects.noise(GrayImage.filled(200, 120, 128), 40.0)).reading.text)
    }

    @Test
    fun `placa curta em pe nao vira de cabeca para baixo`() {
        // placa do jogo com "ZU" e "NU" (2×2 glifos): antes saía "IE IB", girada 180°
        val result = reader.read(ZuRenderer.render("NU ZU", maxPerLine = 2, ink = 240, paper = 40))
        assertEquals("NU ZU", result.reading.text)
        assertEquals(0, result.reading.rotation)
    }
}
