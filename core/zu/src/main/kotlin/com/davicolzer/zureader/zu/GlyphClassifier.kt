package com.davicolzer.zureader.zu

/** Distância de um glifo detectado para um modelo (do alfabeto ou aprendido com o usuário). */
data class GlyphMatch(val glyph: ZuGlyph, val distance: Int, val learned: Boolean = false)

/** Resultado da comparação de um glifo com o alfabeto. */
data class GlyphClassification(
    /** Glifos candidatos, do mais próximo ao mais distante (só os que passaram do limite). */
    val matches: List<GlyphMatch>,
    /** 0..1. Abaixo de [GlyphClassifier.LOW_CONFIDENCE] a letra deve ser marcada como duvidosa. */
    val confidence: Float,
) {
    val best: GlyphMatch? get() = matches.firstOrNull()
    val isGlyph: Boolean get() = matches.isNotEmpty()

    /** O desenho é idêntico a um exemplo ensinado pelo usuário: a letra dele vale, sem depender do contexto. */
    val taughtByUser: Boolean get() = best?.let { it.learned && it.distance == 0 } == true
}

/**
 * Compara um glifo (já normalizado para a grade 5×5) com os modelos do alfabeto,
 * pela distância de Hamming, sem girar: no Zu a rotação muda a letra.
 *
 * [learned] são exemplos ensinados pelo usuário ao corrigir letras (desenho como foi lido → letra certa).
 * Um exemplo só vence se estiver mais perto que o modelo do alfabeto; no empate, vale o alfabeto.
 */
class GlyphClassifier(
    private val alphabet: List<ZuGlyph> = ZuAlphabet.glyphs,
    /** Acima desta distância o desenho não é considerado um glifo. */
    private val maxDistance: Int = MAX_DISTANCE,
    learned: List<ZuGlyph> = emptyList(),
) {
    private val templates: List<Pair<ZuGlyph, Boolean>> = alphabet.map { it to false } + learned.map { it to true }

    /**
     * Folga que vale confiança total: a menor distância entre desenhos de símbolos diferentes do
     * conjunto, até 2. Nas letras é 2; nos números, que só mudam no miolo 3×3, alguns ficam a 1 bloco.
     */
    private val fullMargin: Int = alphabet.flatMap { a ->
        alphabet.filter { b -> b.letters.none { it in a.letters } }.map { b -> a.pattern.hammingDistance(b.pattern) }
    }.minOrNull()?.coerceIn(1, 2) ?: 2

    fun classify(bits: BitMatrix): GlyphClassification {
        val ranked = templates
            .map { (glyph, isLearned) -> GlyphMatch(glyph, glyph.pattern.hammingDistance(bits), isLearned) }
            .sortedWith(compareBy<GlyphMatch> { it.distance }.thenBy { it.learned })
        val accepted = ranked.filter { it.distance <= maxDistance }
        val best = ranked[0]
        // segundo colocado = o melhor modelo de OUTRA letra (exemplos da mesma letra não tiram confiança)
        val d2 = ranked.firstOrNull { m -> m.glyph.letters.none { it in best.glyph.letters } }?.distance ?: Int.MAX_VALUE
        val confidence = if (best.learned && best.distance == 0) 1f else confidence(best.distance, d2)
        return GlyphClassification(accepted, confidence)
    }

    /**
     * Confiança = folga para o segundo colocado ([fullMargin] blocos ou mais = folga total)
     * × proximidade do modelo (0 blocos diferentes = 1).
     * Letras: exato 1,0; um bloco errado sem empate 0,8; empate 0.
     */
    private fun confidence(d1: Int, d2: Int): Float {
        if (d1 > maxDistance) return 0f
        val margin = (d2 - d1).coerceAtMost(fullMargin) / fullMargin.toFloat()
        val closeness = 1f - d1 / (maxDistance + 1f)
        return margin * closeness
    }

    companion object {
        const val MAX_DISTANCE = 4
        const val LOW_CONFIDENCE = 0.7f
    }
}
