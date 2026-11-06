package com.davicolzer.zureader.zu

/**
 * K/Q e U/V usam o mesmo glifo. Ao escrever à mão, o usuário toca no glifo e a letra é escolhida
 * pelo contexto do inglês, palavra por palavra, como na leitura de uma foto.
 */
object SharedLetters {
    /** Até tantas letras ambíguas numa palavra testa todas as combinações; acima, uma a uma. */
    private const val MAX_EXACT = 10

    /** [text] com cada K/Q e U/V trocado pela letra mais provável no inglês (espaços mantidos). */
    fun resolve(text: String): String = text.split(' ').joinToString(" ") { resolveWord(it) }

    private fun resolveWord(word: String): String {
        val choices = word.map { c -> ZuAlphabet.glyphs.firstOrNull { c in it.letters }?.letters?.takeIf { it.length > 1 } ?: c.toString() }
        val ambiguous = choices.indices.filter { choices[it].length > 1 }
        if (ambiguous.isEmpty()) return word
        if (ambiguous.size <= MAX_EXACT) {
            var best = word
            var bestScore = Int.MIN_VALUE
            for (mask in 0 until (1 shl ambiguous.size)) {
                val chars = word.toCharArray()
                ambiguous.forEachIndexed { k, i -> chars[i] = choices[i][(mask shr k) and 1] }
                val candidate = String(chars)
                val s = score(candidate)
                if (s > bestScore) { bestScore = s; best = candidate }
            }
            return best
        }
        // muitas: decide uma por vez, da esquerda para a direita
        val chars = word.toCharArray()
        for (i in ambiguous) chars[i] = choices[i].maxBy { c -> chars[i] = c; score(String(chars)) }
        return String(chars)
    }

    /** Pontuação da palavra no modelo do inglês (com as fronteiras); números não contam. */
    private fun score(word: String): Int {
        var s = 0
        var prev = EnglishModel.BOUNDARY
        for (c in word) {
            if (c !in 'A'..'Z') { prev = EnglishModel.BOUNDARY; continue }
            s += EnglishModel.logProb(prev, c)
            prev = c
        }
        return s + EnglishModel.logProb(prev, EnglishModel.BOUNDARY)
    }
}
