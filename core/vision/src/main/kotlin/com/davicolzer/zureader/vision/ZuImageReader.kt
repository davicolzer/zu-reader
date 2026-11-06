package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.GlyphClassifier
import com.davicolzer.zureader.zu.PlacedGlyph
import com.davicolzer.zureader.zu.ZuLayout
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
    /** Todos os blocos juntos; o texto separa blocos com quebra de linha. Posições na imagem original. */
    val reading: ZuReading,
    /** Um bloco por tamanho de glifo (ex.: título e corpo), na ordem de leitura. */
    val blocks: List<ZuReading>,
    /** `true` se o texto é escuro sobre fundo claro. */
    val inkIsDark: Boolean,
    val width: Int,
    val height: Int,
    /** Inclinação do texto na imagem, em graus (positivo = sentido horário). */
    val skewDegrees: Float = 0f,
) {
    /**
     * Recorte sugerido: a área do texto com [margin] glifo de folga em volta,
     * limitada à imagem. `null` se nada foi lido.
     */
    fun suggestedCrop(margin: Float = 0.75f): Quad? {
        val glyphs = reading.glyphs
        if (glyphs.isEmpty()) return null
        val pad = glyphs.map { it.source.size }.sorted()[glyphs.size / 2] * margin
        val left = (glyphs.minOf { it.source.x - it.source.width / 2 } - pad).coerceAtLeast(0f)
        val top = (glyphs.minOf { it.source.y - it.source.height / 2 } - pad).coerceAtLeast(0f)
        val right = (glyphs.maxOf { it.source.x + it.source.width / 2 } + pad).coerceAtMost(width.toFloat())
        val bottom = (glyphs.maxOf { it.source.y + it.source.height / 2 } + pad).coerceAtMost(height.toFloat())
        return Quad(Point(left, top), Point(right, top), Point(right, bottom), Point(left, bottom))
    }
}

/**
 * Imagem → texto: limiar de Otsu, correção de inclinação, manchas de tinta, caixas de glifo
 * (agrupadas por tamanho), grade 5×5 de cada glifo e [ZuReader].
 * Testa texto escuro e texto claro e fica com o que encontra mais glifos confiáveis.
 */
class ZuImageReader(
    private val reader: ZuReader = ZuReader(),
    private val classifier: GlyphClassifier = GlyphClassifier(),
) {
    /**
     * @param deskew corrigir inclinação. Desligar deixa a leitura ~2× mais rápida
     * (útil no visor ao vivo da câmera).
     */
    fun read(image: GrayImage, deskew: Boolean = true): ImageReading {
        val threshold = Binarizer.otsu(image)
        return listOf(true, false)
            .map { dark -> readWithPolarity(image, threshold, dark, deskew) }
            .maxBy { it.second }
            .first
    }

    private fun readWithPolarity(image: GrayImage, threshold: Int, inkIsDark: Boolean, deskew: Boolean): Pair<ImageReading, Float> {
        val raw = Binarizer.mask(image, threshold, inkIsDark)
        // Endireitar só pode ajudar: lê como veio e endireitado, e fica com o melhor.
        val angle = if (deskew) Deskew.estimate(raw, image.width, image.height) else 0f
        val straight = readMask(image, Deskew.apply(raw, image.width, image.height, 0f), inkIsDark)
        if (angle == 0f) return straight
        val tilted = readMask(image, Deskew.apply(raw, image.width, image.height, angle), inkIsDark)
        return if (tilted.second > straight.second) tilted else straight
    }

    private fun readMask(image: GrayImage, deskewed: DeskewedMask, inkIsDark: Boolean): Pair<ImageReading, Float> {
        val angle = deskewed.angleDegrees
        val components = Components.find(deskewed.mask, deskewed.width, deskewed.height)
        val groups = GlyphSegmenter.segmentGroups(components)
            .map { boxes ->
                boxes.mapNotNull { box ->
                    val sampled = GlyphSampler.sampleWithQuality(deskewed.mask, deskewed.width, box)
                    if (sampled.crispness < GlyphSampler.MIN_CRISPNESS) return@mapNotNull null
                    PlacedGlyph(box.centerX, box.centerY, box.width.toFloat(), box.height.toFloat(), sampled.bits)
                }.filter { !it.isSolid() && classifier.classify(it.bits).isGlyph }
            }
            .filter { it.isNotEmpty() }

        // A orientação vale para a imagem inteira: soma a pontuação de todos os blocos.
        val (rotation, blocks) = (0..3)
            .map { q -> q to groups.map { reader.read(it, rotation = q) } }
            .maxBy { (q, readings) -> readings.sumOf { it.score } - if (q == 0) 0 else ZuReader.ROTATION_COST }
        val ordered = blocks.zip(groups)
            .sortedBy { (_, glyphs) -> ZuLayout.rotate(glyphs, rotation).minOf { it.y - it.height / 2 } }
            .map { (reading, _) -> reading.toSource(deskewed) }

        val merged = ZuReading(
            text = ordered.joinToString("\n") { it.text },
            glyphs = ordered.flatMap { it.glyphs },
            rotation = rotation,
            direction = ordered.firstOrNull()?.direction ?: ZuReading.EMPTY.direction,
            score = ordered.sumOf { it.score },
        )
        val quality = merged.glyphs.sumOf { it.confidence.toDouble() }.toFloat()
        return ImageReading(merged, ordered, inkIsDark, image.width, image.height, skewDegrees = -angle) to quality
    }

    /** Leva as posições dos glifos de volta para a imagem original (antes de endireitar). */
    private fun ZuReading.toSource(deskewed: DeskewedMask): ZuReading = copy(
        glyphs = glyphs.map { g ->
            val (x, y) = deskewed.toSource(g.source.x, g.source.y)
            g.copy(source = g.source.copy(x = x, y = y))
        },
    )
}
