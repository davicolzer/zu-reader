package com.davicolzer.zureader.data

import com.davicolzer.zureader.zu.BitMatrix
import com.davicolzer.zureader.zu.ZuGlyph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Exemplos aprendidos com as correções do usuário. */
class LearnedGlyphsRepository(private val dao: LearnedGlyphDao) {

    val learned: Flow<List<ZuGlyph>> = dao.observeAll().map { rows ->
        rows.mapNotNull { row -> decode(row.bits)?.let { ZuGlyph(row.letter, it) } }
    }

    /** Guarda [bits] (na orientação de leitura) como a letra [letter]. Um desenho igual já salvo é substituído. */
    suspend fun learn(bits: BitMatrix, letter: Char) {
        dao.upsert(LearnedGlyphEntity(encode(bits), letter.toString(), System.currentTimeMillis()))
        dao.trim(MAX_EXAMPLES)
    }

    suspend fun forget() = dao.clear()

    private fun encode(bits: BitMatrix): String =
        (0 until bits.size * bits.size).joinToString("") { if (bits[it / bits.size, it % bits.size]) "#" else "." }

    private fun decode(text: String): BitMatrix? {
        val n = kotlin.math.sqrt(text.length.toDouble()).toInt()
        if (n * n != text.length || n == 0) return null
        return BitMatrix.of(n) { r, c -> text[r * n + c] == '#' }
    }

    companion object {
        const val MAX_EXAMPLES = 300
    }
}
