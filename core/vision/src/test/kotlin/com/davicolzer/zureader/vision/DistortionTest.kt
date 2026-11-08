package com.davicolzer.zureader.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs

/** Fase 2.5: glifos esticados, tamanhos misturados e inclinação leve. */
class DistortionTest {

    private val reader = ZuImageReader()

    private val sentences = listOf(
        "OPEN THE DOOR",
        "LOOK UP",
        "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG",
        "WALK AROUND",
        "HAVE YOU SEEN THE OWL",
        "DEEP WATER",
    )

    private fun accuracy(effect: (GrayImage) -> GrayImage, blockPx: Int = 8): Double {
        var correct = 0
        var total = 0
        for (s in sentences) {
            val text = reader.read(effect(ZuRenderer.render(s, blockPx = blockPx))).reading.text
            total += s.length
            correct += s.indices.count { it < text.length && text[it] == s[it] }
        }
        return correct.toDouble() / total
    }

    private fun assertAccuracy(name: String, value: Double, minimum: Double = 0.95) {
        println("$name: ${"%.1f".format(value * 100)}%")
        assertTrue(value >= minimum, "$name: ${"%.3f".format(value)} < $minimum")
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.5, 0.75, 1.3, 1.5, 2.0, 3.0])
    fun `esticada na vertical`(factor: Double) {
        assertAccuracy("vertical ${factor}×", accuracy({ ImageEffects.stretch(it, 1.0, factor) }))
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.5, 0.75, 1.3, 1.5, 2.0, 3.0])
    fun `esticada na horizontal`(factor: Double) {
        assertAccuracy("horizontal ${factor}×", accuracy({ ImageEffects.stretch(it, factor, 1.0) }))
    }

    @ParameterizedTest
    @ValueSource(doubles = [-10.0, -6.0, -3.0, 3.0, 6.0, 10.0])
    fun `inclinada`(degrees: Double) {
        assertAccuracy("inclinada $degrees°", accuracy({ ImageEffects.rotate(it, degrees, fill = 230) }, blockPx = 10))
    }

    @Test
    fun `inclinacao medida e devolvida`() {
        val result = reader.read(ImageEffects.rotate(ZuRenderer.render("OPEN THE DOOR", blockPx = 10), 6.0, fill = 230))
        assertEquals("OPEN THE DOOR", result.reading.text)
        assertTrue(abs(result.skewDegrees - 6f) <= 1f, "inclinação medida: ${result.skewDegrees}")
    }

    @Test
    fun `inclinada e esticada com ruido`() {
        assertAccuracy(
            "inclinada 5° + vertical 1,6× + JPEG",
            accuracy({ ImageEffects.jpeg(ImageEffects.rotate(ImageEffects.stretch(it, 1.0, 1.6), 5.0, fill = 230), 0.6f) }, blockPx = 10),
        )
    }

    @Test
    fun `titulo grande e texto pequeno na mesma imagem`() {
        val title = ZuRenderer.render("LOOK UP", maxPerLine = 8, blockPx = 16)
        val body = ZuRenderer.render("THE TREE OF LIFE", maxPerLine = 6, blockPx = 6)
        val image = ImageEffects.stack(listOf(title, body), gap = 30, fill = 230)
        val result = reader.read(image)
        assertEquals(2, result.blocks.size)
        assertEquals("LOOK UP\nTHE TREE OF LIFE", result.reading.text)
    }

    @Test
    fun `tamanhos misturados`() {
        val title = ZuRenderer.render("OPEN THE DOOR", maxPerLine = 4, blockPx = 14)
        val body = ZuRenderer.render("WALK AROUND THE TREE", maxPerLine = 6, blockPx = 7)
        val image = ImageEffects.stack(listOf(title, body), gap = 30, fill = 230)
        val result = reader.read(image)
        assertEquals(0, result.reading.rotation)
        assertEquals("OPEN THE DOOR\nWALK AROUND THE TREE", result.reading.text)
    }
}
