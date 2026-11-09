package com.davicolzer.zureader.vision

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Sequências de comandos geradas: peças sorteadas desenhadas como blocos, numa coluna ou numa
 * linha, coladas umas nas outras ou separadas. A leitura tem que devolver a mesma sequência
 * (com a coluna "deitada", como o jogo manda ler).
 */
class CommandSyntheticTest {
    /** As peças que aparecem nos códigos do jogo: setas (T), pular (O) e girar (S, Z). */
    private val used = listOf(
        CommandPiece(Tetromino.T, 0), CommandPiece(Tetromino.T, 1), CommandPiece(Tetromino.T, 2), CommandPiece(Tetromino.T, 3),
        CommandPiece(Tetromino.O, 0), CommandPiece(Tetromino.S, 0), CommandPiece(Tetromino.Z, 0),
    )

    private fun turnClockwise(piece: CommandPiece): List<Cell> = rotateClockwise(piece.cells)

    /**
     * Desenha a sequência. [vertical]: cada peça em pé (girada ¼ de volta no sentido horário,
     * como fica numa coluna), uma embaixo da outra. [glued]: encostadas, sem espaço; senão, um
     * bloco de espaço. Fundo escuro e peças claras (ou o contrário), com um pouco de ruído.
     */
    private fun render(pieces: List<CommandPiece>, vertical: Boolean, glued: Boolean, random: Random, block: Int = 12, darkInk: Boolean = false): RgbImage {
        // posições em blocos: cada peça entra depois da anterior no eixo; no glued, encostada
        val placed = mutableListOf<List<Cell>>()
        var cursor = 1
        for (piece in pieces) {
            val cells = if (vertical) turnClockwise(piece) else piece.cells
            val along = { c: Cell -> if (vertical) c.row else c.col }
            val across = { c: Cell -> if (vertical) c.col else c.row }
            // no glued, desloca a peça no eixo transversal para encostar na anterior
            // posição transversal da peça: a primeira na coluna 3; no glued, uma célula da primeira
            // fileira da nova peça fica logo depois de uma célula da última fileira da anterior
            val offset = if (glued && placed.isNotEmpty()) {
                val prev = placed.last()
                val prevEnd = prev.maxOf(along)
                val lastRow = prev.filter { along(it) == prevEnd }.map(across)
                val firstRow = cells.filter { along(it) == 0 }.map(across)
                lastRow.random(random) - firstRow.random(random)
            } else 3
            val start = if (glued && placed.isNotEmpty()) placed.last().maxOf(along) + 1 else cursor
            val moved = cells.map { c ->
                if (vertical) Cell(c.row + start, c.col + offset) else Cell(c.row + offset, c.col + start)
            }
            placed += moved
            cursor = moved.maxOf(along) + 2
        }
        val all = placed.flatten()
        val minR = all.minOf { it.row } - 1
        val minC = all.minOf { it.col } - 1
        val rows = all.maxOf { it.row } - minR + 2
        val cols = all.maxOf { it.col } - minC + 2
        val w = cols * block
        val h = rows * block
        val ink = all.map { Cell(it.row - minR, it.col - minC) }.toSet()
        val (bg, fg) = if (darkInk) 200 to 60 else 70 to 230
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val v = (if (Cell(y / block, x / block) in ink) fg else bg) + random.nextInt(-12, 13)
            val g = v.coerceIn(0, 255)
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return RgbImage(w, h, px)
    }

    /**
     * Lê [runs] sequências sorteadas e exige [minOk] de acerto (a sequência inteira certa). Peças
     * coladas podem ter mais de uma divisão possível (ex.: uma escada de Z), então não chega a 100%.
     */
    private fun check(vertical: Boolean, glued: Boolean, runs: Int = 40, minOk: Double = 0.85) {
        val random = Random(if (vertical) 11 else 22 + if (glued) 1 else 0)
        var ok = 0
        var slowest = 0L
        var total = 0L
        val misses = mutableListOf<String>()
        repeat(runs) { n ->
            val pieces = List(random.nextInt(4, 9)) { used.random(random) }
            val image = render(pieces, vertical, glued, random, block = random.nextInt(9, 16), darkInk = n % 3 == 0)
            val expected = CommandReading(pieces).code
            val started = System.nanoTime()
            val got = CommandReader.read(image).code
            val ms = (System.nanoTime() - started) / 1_000_000
            if (ms > slowest) slowest = ms
            total += ms
            if (got == expected) ok++ else if (misses.size < 5) misses += "esperado '$expected', lido '$got'"
        }
        val rate = ok.toDouble() / runs
        println("${if (vertical) "coluna" else "linha"} ${if (glued) "coladas" else "separadas"}: $ok/$runs, média ${total / runs} ms, mais lenta $slowest ms ${misses.joinToString("; ")}")
        assertTrue(rate >= minOk, "acerto ${"%.0f".format(rate * 100)}%: ${misses.joinToString("; ")}")
    }

    @Test
    fun `coluna de pecas separadas`() = check(vertical = true, glued = false)

    @Test
    fun `coluna de pecas coladas`() = check(vertical = true, glued = true)

    @Test
    fun `linha de pecas separadas`() = check(vertical = false, glued = false)

    @Test
    fun `linha de pecas coladas`() = check(vertical = false, glued = true)
}
