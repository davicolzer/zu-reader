package com.davicolzer.zureader.zu

/**
 * Glifo como uma grade quadrada de blocos (true = bloco preenchido).
 */
class BitMatrix private constructor(
    val size: Int,
    private val bits: BooleanArray,
) {
    operator fun get(row: Int, col: Int): Boolean = bits[row * size + col]

    /** Gira 90° no sentido horário. */
    fun rotate90(): BitMatrix {
        val out = BooleanArray(bits.size)
        for (r in 0 until size) for (c in 0 until size) {
            out[c * size + (size - 1 - r)] = this[r, c]
        }
        return BitMatrix(size, out)
    }

    /** Gira [quarterTurns] × 90° no sentido horário (aceita valores negativos). */
    fun rotate(quarterTurns: Int): BitMatrix {
        var m = this
        repeat(Math.floorMod(quarterTurns, 4)) { m = m.rotate90() }
        return m
    }

    /** Cópia com o bloco em ([row], [col]) invertido. Útil para simular ruído. */
    fun toggled(row: Int, col: Int): BitMatrix {
        val out = bits.copyOf()
        out[row * size + col] = !out[row * size + col]
        return BitMatrix(size, out)
    }

    /** As quatro orientações possíveis: 0°, 90°, 180° e 270°. */
    fun rotations(): List<BitMatrix> = generateSequence(this) { it.rotate90() }.take(4).toList()

    /** Quantidade de blocos diferentes entre duas matrizes do mesmo tamanho. */
    fun hammingDistance(other: BitMatrix): Int {
        require(other.size == size) { "Tamanhos diferentes: $size e ${other.size}" }
        return bits.indices.count { bits[it] != other.bits[it] }
    }

    override fun equals(other: Any?): Boolean =
        other is BitMatrix && other.size == size && other.bits.contentEquals(bits)

    override fun hashCode(): Int = 31 * size + bits.contentHashCode()

    override fun toString(): String =
        (0 until size).joinToString("\n") { r -> (0 until size).joinToString("") { c -> if (this[r, c]) "#" else "." } }

    companion object {
        /** Cria uma matriz [size]×[size] perguntando cada bloco a [filled]. */
        fun of(size: Int, filled: (row: Int, col: Int) -> Boolean): BitMatrix {
            require(size > 0) { "Matriz vazia" }
            val bits = BooleanArray(size * size) { filled(it / size, it % size) }
            return BitMatrix(size, bits)
        }

        /**
         * Lê uma matriz a partir de linhas de texto: `#` ou `1` = bloco, `.` ou `0` = vazio.
         */
        fun parse(vararg rows: String): BitMatrix {
            val n = rows.size
            require(n > 0) { "Matriz vazia" }
            require(rows.all { it.length == n }) { "A matriz precisa ser quadrada ($n×$n)" }
            val bits = BooleanArray(n * n)
            rows.forEachIndexed { r, line ->
                line.forEachIndexed { c, ch ->
                    bits[r * n + c] = when (ch) {
                        '#', '1' -> true
                        '.', '0' -> false
                        else -> throw IllegalArgumentException("Caractere inválido '$ch' na linha $r")
                    }
                }
            }
            return BitMatrix(n, bits)
        }
    }
}
