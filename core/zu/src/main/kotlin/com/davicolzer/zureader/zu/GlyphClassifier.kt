package com.davicolzer.zureader.zu

/** Distância de um glifo detectado para um glifo do alfabeto. */
data class GlyphMatch(val glyph: ZuGlyph, val distance: Int)

/** Resultado da comparação de um glifo com o alfabeto. */
data class GlyphClassification(
    /** Glifos candidatos, do mais próximo ao mais distante (só os que passaram do limite). */
    val matches: List<GlyphMatch>,
    /** 0..1. Abaixo de [GlyphClassifier.LOW_CONFIDENCE] a letra deve ser marcada como duvidosa. */
    val confidence: Float,
) {
    val best: GlyphMatch? get() = matches.firstOrNull()
    val isGlyph: Boolean get() = matches.isNotEmpty()
}

/**
 * Compara um glifo (já normalizado para a grade 5×5) com os modelos do alfabeto,
 * pela distância de Hamming, sem girar: no Zu a rotação muda a letra.
 */
class GlyphClassifier(
    private val alphabet: List<ZuGlyph> = ZuAlphabet.glyphs,
    /** Acima desta distância o desenho não é considerado um glifo. */
    private val maxDistance: Int = MAX_DISTANCE,
) {
    fun classify(bits: BitMatrix): GlyphClassification {
        val ranked = alphabet
            .map { GlyphMatch(it, it.pattern.hammingDistance(bits)) }
            .sortedBy { it.distance }
        val accepted = ranked.filter { it.distance <= maxDistance }
        val d1 = ranked[0].distance
        val d2 = ranked.getOrNull(1)?.distance ?: Int.MAX_VALUE
        return GlyphClassification(accepted, confidence(d1, d2))
    }

    /**
     * Confiança = folga para o segundo colocado (2 blocos ou mais = folga total)
     * × proximidade do modelo (0 blocos diferentes = 1).
     * Exato: 1,0. Um bloco errado sem empate: 0,8. Empate: 0.
     */
    private fun confidence(d1: Int, d2: Int): Float {
        if (d1 > maxDistance) return 0f
        val margin = (d2 - d1).coerceAtMost(2) / 2f
        val closeness = 1f - d1 / (maxDistance + 1f)
        return margin * closeness
    }

    companion object {
        const val MAX_DISTANCE = 4
        const val LOW_CONFIDENCE = 0.7f
    }
}
