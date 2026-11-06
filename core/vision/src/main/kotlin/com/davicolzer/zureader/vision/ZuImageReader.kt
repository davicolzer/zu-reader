package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.GlyphClassifier
import com.davicolzer.zureader.zu.PlacedGlyph
import com.davicolzer.zureader.zu.ZuReader
import com.davicolzer.zureader.zu.ZuReading

/** Nenhum glifo Zu é todo preenchido; um quadrado sólido é parede, objeto ou sujeira. */
private fun PlacedGlyph.isSolid(): Boolean {
    var filled = 0
    for (r in 0 until bits.size) for (c in 0 until bits.size) if (bits[r, c]) filled++
    return filled >= bits.size * bits.size - 1
}

/** Resultado da leitura de uma imagem. */
data class ImageReading(
    val reading: ZuReading,
    /** `true` se o texto é escuro sobre fundo claro. */
    val inkIsDark: Boolean,
    val width: Int,
    val height: Int,
)

/**
 * Imagem → texto: limiar de Otsu, manchas de tinta, caixas de glifo, grade 5×5 e [ZuReader].
 * Testa texto escuro e texto claro e fica com o que encontra mais glifos confiáveis.
 */
class ZuImageReader(
    private val reader: ZuReader = ZuReader(),
    private val classifier: GlyphClassifier = GlyphClassifier(),
) {
    fun read(image: GrayImage): ImageReading {
        val threshold = Binarizer.otsu(image)
        return listOf(true, false)
            .map { dark -> readWithPolarity(image, threshold, dark) }
            .maxBy { it.second }
            .first
    }

    private fun readWithPolarity(image: GrayImage, threshold: Int, inkIsDark: Boolean): Pair<ImageReading, Float> {
        val mask = Binarizer.mask(image, threshold, inkIsDark)
        val boxes = GlyphSegmenter.segment(Components.find(mask, image.width, image.height))
        val glyphs = boxes
            .map { box -> PlacedGlyph(box.centerX, box.centerY, box.size.toFloat(), GlyphSampler.sample(mask, image.width, box)) }
            .filter { !it.isSolid() && classifier.classify(it.bits).isGlyph }
        val reading = reader.read(glyphs)
        val quality = reading.glyphs.sumOf { it.confidence.toDouble() }.toFloat()
        return ImageReading(reading, inkIsDark, image.width, image.height) to quality
    }
}
