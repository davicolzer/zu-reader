package com.davicolzer.zureader.data

/**
 * "Já li isso antes?": compara o texto de uma leitura nova com os textos já salvos na coleção.
 * Ignora espaços, quebras e pontuação, e trata K/Q e U/V como iguais (mesmo glifo no Zu).
 * Textos com pelo menos [MIN_FUZZY] caracteres aceitam pequenas diferenças (uma letra lida
 * diferente numa foto nova); os curtos precisam ser iguais.
 */
object SameText {
    /** A partir de tantos caracteres, aceita diferenças pequenas. */
    const val MIN_FUZZY = 6

    /** Semelhança mínima (1 = igual) para textos longos. */
    const val MIN_SIMILARITY = 0.9

    /** O texto só com o que conta para comparar. */
    fun key(text: String): String =
        text.uppercase().filter { it.isLetterOrDigit() || it == '#' || it == '=' }.replace('Q', 'K').replace('V', 'U')

    /** 1 − distância de edição / tamanho do maior (0 a 1). */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var diagonal = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val up = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, diagonal + if (a[i - 1] == b[j - 1]) 0 else 1)
                diagonal = up
            }
        }
        return 1.0 - d[b.length].toDouble() / maxOf(a.length, b.length)
    }

    /** Semelhança entre o texto lido e um salvo, ou `null` se não contam como o mesmo texto. */
    fun match(read: String, saved: String): Double? {
        val a = key(read)
        val b = key(saved)
        if (a.isEmpty() || b.isEmpty()) return null
        if (a.length < MIN_FUZZY || b.length < MIN_FUZZY) return if (a == b) 1.0 else null
        return similarity(a, b).takeIf { it >= MIN_SIMILARITY }
    }

    /** O salvo mais parecido com [read] (em empate, o primeiro da lista: o mais novo), ou `null`. */
    fun <T> best(read: String, saved: List<T>, text: (T) -> String): T? =
        saved.mapNotNull { s -> match(read, text(s))?.let { s to it } }.maxByOrNull { it.second }?.first
}
