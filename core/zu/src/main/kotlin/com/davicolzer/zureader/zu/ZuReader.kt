package com.davicolzer.zureader.zu

/** Letra alternativa para a tela de correção, com nota de 0 a 1. */
data class LetterScore(val letter: Char, val score: Float)

/** Leitura de um glifo dentro do texto. */
data class GlyphReading(
    /** Glifo como veio da imagem (antes de qualquer rotação). */
    val source: PlacedGlyph,
    /** Letra escolhida, ou [ZuReader.UNKNOWN] se o desenho não parece um glifo. */
    val letter: Char,
    /** 0..1; abaixo de [GlyphClassifier.LOW_CONFIDENCE] a letra é duvidosa. */
    val confidence: Float,
    /** Outras letras possíveis, da mais provável à menos provável (até 3). */
    val alternatives: List<LetterScore>,
    /** `true` se a letra foi escolhida pelo usuário. */
    val corrected: Boolean = false,
) {
    val isLowConfidence: Boolean get() = !corrected && confidence < GlyphClassifier.LOW_CONFIDENCE
}

/** Resultado da leitura de um texto em Zu. */
data class ZuReading(
    /** Texto em inglês, com espaços entre palavras. */
    val text: String,
    /** Glifos na ordem de leitura. */
    val glyphs: List<GlyphReading>,
    /** Quartos de volta no sentido horário aplicados à imagem para ler o texto em pé. */
    val rotation: Int,
    val direction: ReadingDirection,
    /** Pontuação da leitura (log-probabilidade, em milésimos de nat). Só serve para comparar. */
    val score: Int,
) {
    /**
     * Troca a letra do glifo [index] (na ordem de leitura) por [letter], marcando como corrigida.
     * Espaços e quebras de linha do texto são mantidos.
     */
    fun withLetter(index: Int, letter: Char): ZuReading {
        require(index in glyphs.indices) { "Glifo $index não existe (há ${glyphs.size})" }
        require(!letter.isWhitespace()) { "Símbolo inválido: '$letter'" }
        val updated = glyphs.toMutableList()
        updated[index] = glyphs[index].copy(letter = letter, confidence = 1f, corrected = true)
        var g = 0
        val newText = buildString {
            for (ch in text) append(if (ch == ' ' || ch == '\n') ch else updated[g++].letter)
        }
        return copy(text = newText, glyphs = updated)
    }

    companion object {
        val EMPTY = ZuReading("", emptyList(), 0, ReadingDirection.VERTICAL_RIGHT_TO_LEFT, 0)
    }
}

/**
 * Transforma glifos posicionados em texto.
 *
 * 1. Para cada orientação possível (ou só a informada), gira a disposição e os desenhos.
 * 2. Ordena os glifos (colunas da direita para a esquerda, por padrão).
 * 3. Compara cada glifo com o alfabeto.
 * 4. Escolhe a sequência de letras mais provável com Viterbi sobre pares de letras do inglês.
 *    Isso resolve K/Q, U/V e glifos com ruído.
 * 5. Fica com a orientação de maior pontuação. Girar a imagem tem um custo fixo,
 *    porque fotos quase sempre estão em pé e textos curtos são ambíguos.
 */
class ZuReader(
    private val classifier: GlyphClassifier = GlyphClassifier(),
    private val direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
    /** Usar o modelo de pares de letras do inglês (desligado para números). */
    private val languageModel: Boolean = true,
) {
    /**
     * @param rotation se informado (0..3), usa só essa orientação.
     */
    fun read(glyphs: List<PlacedGlyph>, rotation: Int? = null): ZuReading {
        if (glyphs.isEmpty()) return ZuReading.EMPTY.copy(direction = direction)
        val rotations = rotation?.let { listOf(Math.floorMod(it, 4)) } ?: (0..3).toList()
        return rotations
            .map { q -> readRotated(glyphs, q) }
            .maxBy { it.score - if (it.rotation == 0) 0 else ROTATION_COST }
    }

    private fun readRotated(original: List<PlacedGlyph>, quarterTurns: Int): ZuReading {
        val rotated = ZuLayout.rotate(original, quarterTurns)
        val tokens = ZuLayout.order(rotated, direction)

        // Uma posição por token: espaço, glifo desconhecido ou lista de candidatos.
        val slots = tokens.map { token ->
            when (token) {
                LayoutToken.Space -> Slot.Space
                is LayoutToken.Glyph -> {
                    val classification = classifier.classify(rotated[token.index].bits)
                    if (classification.isGlyph) Slot.Letters(token.index, classification) else Slot.Unknown(token.index)
                }
            }
        }

        val (letters, score) = viterbi(slots)

        val text = StringBuilder()
        val readings = mutableListOf<GlyphReading>()
        slots.forEachIndexed { i, slot ->
            when (slot) {
                Slot.Space -> text.append(' ')
                is Slot.Unknown -> {
                    text.append(UNKNOWN)
                    readings += GlyphReading(original[slot.index], UNKNOWN, 0f, emptyList())
                }
                is Slot.Letters -> {
                    val chosen = letters[i]
                    text.append(chosen)
                    readings += GlyphReading(original[slot.index], chosen, slot.classification.confidence, alternatives(slot.classification, chosen))
                }
            }
        }
        return ZuReading(text.toString().trim(), readings, quarterTurns, direction, score)
    }

    private sealed interface Slot {
        data object Space : Slot
        data class Unknown(val index: Int) : Slot
        data class Letters(val index: Int, val classification: GlyphClassification) : Slot {
            /** Candidatos: glifos até 1 bloco mais longe que o melhor, com custo pela distância. */
            val candidates: List<Pair<Char, Int>> by lazy {
                // desenho ensinado pelo usuário: só a letra dele
                if (classification.taughtByUser) return@lazy classification.matches.first().glyph.letters.map { it to 0 }
                val best = classification.matches.first().distance
                classification.matches
                    .filter { it.distance <= best + 1 }
                    .flatMap { m -> m.glyph.letters.map { it to -m.distance * BIT_COST } }
            }
        }
    }

    /** Melhor letra para cada posição (espaços e desconhecidos ficam como fronteira). */
    private fun viterbi(slots: List<Slot>): Pair<CharArray, Int> {
        val boundary = EnglishModel.BOUNDARY
        // estado = última letra; valor = (pontuação, de onde veio)
        var states: Map<Char, Int> = mapOf(boundary to 0)
        val back = arrayOfNulls<Map<Char, Char>>(slots.size)

        slots.forEachIndexed { i, slot ->
            val options: List<Pair<Char, Int>> = when (slot) {
                is Slot.Letters -> slot.candidates
                else -> listOf(boundary to 0)
            }
            val next = HashMap<Char, Int>()
            val from = HashMap<Char, Char>()
            for ((letter, emission) in options) {
                var bestScore = Int.MIN_VALUE
                var bestPrev = boundary
                for ((prev, s) in states) {
                    val candidate = s + transition(prev, letter) + emission
                    if (candidate > bestScore) { bestScore = candidate; bestPrev = prev }
                }
                if (bestScore > (next[letter] ?: Int.MIN_VALUE)) {
                    next[letter] = bestScore
                    from[letter] = bestPrev
                }
            }
            states = next
            back[i] = from
        }

        // fecha a última palavra
        var last = boundary
        var total = Int.MIN_VALUE
        for ((prev, s) in states) {
            val candidate = s + transition(prev, boundary)
            if (candidate > total) { total = candidate; last = prev }
        }

        val out = CharArray(slots.size)
        for (i in slots.indices.reversed()) {
            out[i] = last
            last = back[i]!![last] ?: boundary
        }
        return out to total
    }

    private fun transition(prev: Char, next: Char): Int = if (languageModel) EnglishModel.logProb(prev, next) else 0

    private fun alternatives(classification: GlyphClassification, chosen: Char): List<LetterScore> =
        classification.matches
            .flatMap { m -> m.glyph.letters.map { LetterScore(it, 1f - m.distance / (GlyphClassifier.MAX_DISTANCE + 1f)) } }
            .filter { it.letter != chosen }
            .distinctBy { it.letter }
            .take(3)

    companion object {
        const val UNKNOWN = '?'

        /** Custo de cada bloco diferente do modelo (milésimos de nat). */
        const val BIT_COST = 2_000

        /** Custo fixo de girar a imagem (milésimos de nat, ≈ 5% de probabilidade a priori). */
        const val ROTATION_COST = 3_000
    }
}
