package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.BitMatrix
import com.davicolzer.zureader.zu.GlyphReading
import com.davicolzer.zureader.zu.LetterScore
import com.davicolzer.zureader.zu.PlacedGlyph
import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ZuReading

/**
 * Grava e lê uma [ImageReading] como texto, para o histórico.
 *
 * Formato (uma linha por item, campos separados por `;`):
 * ```
 * zu1
 * w;h;rotação;inclinação;tinta escura;direção;pontuação
 * texto (com | no lugar de quebra de linha)
 * x;y;largura;altura;25 blocos (# ou .);letra;confiança;corrigida;alternativas (letra:nota,...)
 * ...
 * ```
 */
object ReadingCodec {
    private const val VERSION = "zu1"

    fun encode(reading: ImageReading): String = buildString {
        val r = reading.reading
        appendLine(VERSION)
        appendLine(listOf(reading.width, reading.height, r.rotation, reading.skewDegrees, if (reading.inkIsDark) 1 else 0, r.direction.name, r.score).joinToString(";"))
        appendLine(r.text.replace('\n', '|'))
        for (g in r.glyphs) {
            val s = g.source
            val bits = (0 until s.bits.size * s.bits.size).joinToString("") { if (s.bits[it / s.bits.size, it % s.bits.size]) "#" else "." }
            val alternatives = g.alternatives.joinToString(",") { "${it.letter}:${it.score}" }
            appendLine(listOf(s.x, s.y, s.width, s.height, bits, g.letter, g.confidence, if (g.corrected) 1 else 0, alternatives).joinToString(";"))
        }
    }

    fun decode(data: String): ImageReading {
        val lines = data.lines()
        require(lines.size >= 3 && lines[0] == VERSION) { "Formato desconhecido" }
        val head = lines[1].split(";")
        val text = lines[2].replace('|', '\n')
        val glyphs = lines.drop(3).filter { it.isNotEmpty() }.map { line ->
            val f = line.split(";")
            val n = kotlin.math.sqrt(f[4].length.toDouble()).toInt()
            val bits = BitMatrix.of(n) { r, c -> f[4][r * n + c] == '#' }
            GlyphReading(
                source = PlacedGlyph(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), bits),
                letter = f[5].single(),
                confidence = f[6].toFloat(),
                alternatives = if (f[8].isEmpty()) emptyList() else f[8].split(",").map {
                    val (letter, score) = it.split(":")
                    LetterScore(letter.single(), score.toFloat())
                },
                corrected = f[7] == "1",
            )
        }
        val reading = ZuReading(
            text = text,
            glyphs = glyphs,
            rotation = head[2].toInt(),
            direction = ReadingDirection.valueOf(head[5]),
            score = head[6].toInt(),
        )
        return ImageReading(
            reading = reading,
            blocks = listOf(reading),
            inkIsDark = head[4] == "1",
            width = head[0].toInt(),
            height = head[1].toInt(),
            skewDegrees = head[3].toFloat(),
        )
    }
}
