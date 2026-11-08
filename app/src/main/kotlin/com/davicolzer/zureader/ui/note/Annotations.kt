package com.davicolzer.zureader.ui.note

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.util.Base64

/** Ferramentas do editor de anotações. [MOVE] seleciona um desenho para mover, recolorir ou apagar. */
enum class NoteTool { MOVE, ARROW, RECTANGLE, ELLIPSE, TEXT }

/**
 * Um desenho sobre a foto, em pixels da imagem (não da tela), para sair igual na foto salva.
 * [color] em ARGB.
 */
sealed interface Mark {
    val color: Int

    /** Seta de [from] (cauda) até [to] (ponta). */
    data class Arrow(val from: Offset, val to: Offset, override val color: Int) : Mark

    /** Retângulo ou círculo (elipse) entre dois cantos. */
    data class Shape(val from: Offset, val to: Offset, val ellipse: Boolean, override val color: Int) : Mark

    /** Texto escrito a partir de [at] (canto de cima à esquerda). */
    data class Label(val at: Offset, val text: String, override val color: Int) : Mark
}

/** O mesmo desenho deslocado de [delta] (em pixels da imagem). */
fun Mark.moved(delta: Offset): Mark = when (this) {
    is Mark.Arrow -> copy(from = from + delta, to = to + delta)
    is Mark.Shape -> copy(from = from + delta, to = to + delta)
    is Mark.Label -> copy(at = at + delta)
}

/** O mesmo desenho na foto redimensionada por [factor] (ex.: ao reduzir para HD). */
fun Mark.scaled(factor: Float): Mark = when (this) {
    is Mark.Arrow -> copy(from = from * factor, to = to * factor)
    is Mark.Shape -> copy(from = from * factor, to = to * factor)
    is Mark.Label -> copy(at = at * factor)
}

/** O mesmo desenho com outra cor. */
fun Mark.recolored(argb: Int): Mark = when (this) {
    is Mark.Arrow -> copy(color = argb)
    is Mark.Shape -> copy(color = argb)
    is Mark.Label -> copy(color = argb)
}

/**
 * Caixa que envolve o desenho (pixels da imagem). O texto é estimado pelo tamanho da letra
 * ([textSize]): ~0,6 de largura por caractere e uma linha por quebra.
 */
fun Mark.bounds(textSize: Float): Rect = when (this) {
    is Mark.Arrow -> Rect(minOf(from.x, to.x), minOf(from.y, to.y), maxOf(from.x, to.x), maxOf(from.y, to.y))
    is Mark.Shape -> Rect(minOf(from.x, to.x), minOf(from.y, to.y), maxOf(from.x, to.x), maxOf(from.y, to.y))
    is Mark.Label -> {
        val rows = text.lines()
        Rect(at.x, at.y, at.x + rows.maxOf { it.length } * textSize * 0.6f, at.y + rows.size * textSize * 1.2f)
    }
}

/**
 * Desenhos depois de recortar a foto em ([left], [top]) com [width] x [height]: deslocados para a
 * foto nova; os que ficaram totalmente de fora saem.
 */
fun cropMarks(marks: List<Mark>, left: Float, top: Float, width: Int, height: Int, textSize: Float = 0f): List<Mark> {
    val area = Rect(0f, 0f, width.toFloat(), height.toFloat())
    return marks.map { it.moved(Offset(-left, -top)) }.filter { it.bounds(textSize).overlaps(area) }
}

/**
 * O desenho de cima que está em [p] (com folga [tolerance], em pixels da imagem), ou `null`.
 * Riscos contam perto da linha; retângulos e círculos, na borda ou dentro; textos, na caixa.
 */
fun hitMark(marks: List<Mark>, p: Offset, tolerance: Float, textSize: Float): Int? {
    for (i in marks.indices.reversed()) {
        val hit = when (val m = marks[i]) {
            is Mark.Arrow -> segmentDistance(p, m.from, m.to) <= tolerance
            is Mark.Shape -> m.bounds(textSize).inflate(tolerance).contains(p)
            is Mark.Label -> m.bounds(textSize).inflate(tolerance).contains(p)
        }
        if (hit) return i
    }
    return null
}

private fun segmentDistance(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

/**
 * Grava e lê os desenhos de uma anotação (para "Anotar mais" poder mexer neles depois), junto com o
 * nome do arquivo da foto original (sem desenhos). Uma linha por desenho:
 * `A;cor;x1;y1;x2;y2` (seta, da cauda à ponta), `R`/`E;cor;x1;y1;x2;y2` (retângulo/círculo), `T;cor;x;y;texto Base64`.
 */
object MarkCodec {
    private const val VERSION = "note1"

    data class Decoded(val originalFile: String, val marks: List<Mark>)

    fun encode(originalFile: String, marks: List<Mark>): String = buildString {
        appendLine(VERSION)
        appendLine(originalFile)
        for (m in marks) {
            appendLine(
                when (m) {
                    is Mark.Arrow -> "A;${m.color};${m.from.x};${m.from.y};${m.to.x};${m.to.y}"
                    is Mark.Shape -> "${if (m.ellipse) "E" else "R"};${m.color};${m.from.x};${m.from.y};${m.to.x};${m.to.y}"
                    is Mark.Label -> "T;${m.color};${m.at.x};${m.at.y};" + Base64.getEncoder().encodeToString(m.text.toByteArray(Charsets.UTF_8))
                },
            )
        }
    }

    /** `null` se não é deste formato (anotações antigas: só a foto já desenhada). */
    fun decode(data: String): Decoded? {
        val lines = data.lines()
        if (lines.size < 2 || lines[0] != VERSION || lines[1].isBlank()) return null
        val marks = lines.drop(2).filter { it.isNotBlank() }.mapNotNull { line ->
            runCatching {
                val f = line.split(";")
                val color = f[1].toInt()
                when (f[0]) {
                    "A" -> Mark.Arrow(Offset(f[2].toFloat(), f[3].toFloat()), Offset(f[4].toFloat(), f[5].toFloat()), color)
                    "R", "E" -> Mark.Shape(Offset(f[2].toFloat(), f[3].toFloat()), Offset(f[4].toFloat(), f[5].toFloat()), f[0] == "E", color)
                    "T" -> Mark.Label(Offset(f[2].toFloat(), f[3].toFloat()), String(Base64.getDecoder().decode(f[4]), Charsets.UTF_8), color)
                    else -> null
                }
            }.getOrNull()
        }
        return Decoded(lines[1], marks)
    }
}

/**
 * As duas pontas da cabeça da seta (as "farpas"), para desenhar como dois traços saindo de [to].
 * O tamanho acompanha o traço ([strokeWidth]) e nunca passa de metade da seta.
 */
fun arrowHead(from: Offset, to: Offset, strokeWidth: Float): Pair<Offset, Offset> {
    val d = to - from
    val length = d.getDistance()
    if (length == 0f) return to to to
    val head = minOf(strokeWidth * 5f, length / 2f)
    val ux = d.x / length
    val uy = d.y / length
    // 30° para cada lado da linha
    val cos = 0.866f
    val sin = 0.5f
    val back = Offset(-ux, -uy)
    val left = Offset(back.x * cos - back.y * sin, back.x * sin + back.y * cos)
    val right = Offset(back.x * cos + back.y * sin, -back.x * sin + back.y * cos)
    return (to + left * head) to (to + right * head)
}

/** Espessura do traço e tamanho do texto, proporcionais à foto (iguais na tela e na foto salva). */
object MarkStyle {
    fun strokeWidth(imageWidth: Int, imageHeight: Int): Float = maxOf(imageWidth, imageHeight) * 0.008f

    fun textSize(imageWidth: Int, imageHeight: Int): Float = maxOf(imageWidth, imageHeight) * 0.06f
}

/** Desenha [marks] por cima de [image] e devolve a foto anotada (nova imagem, a original não muda). */
fun renderMarks(image: Bitmap, marks: List<Mark>): Bitmap {
    val out = image.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val width = MarkStyle.strokeWidth(out.width, out.height)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = MarkStyle.textSize(out.width, out.height)
        typeface = Typeface.DEFAULT_BOLD
        // contorno escuro em volta das letras, para ler em qualquer fundo
        setShadowLayer(width, 0f, 0f, android.graphics.Color.BLACK)
    }
    for (mark in marks) {
        when (mark) {
            is Mark.Arrow -> {
                line.color = mark.color
                val (l, r) = arrowHead(mark.from, mark.to, width)
                val path = Path().apply {
                    moveTo(mark.from.x, mark.from.y)
                    lineTo(mark.to.x, mark.to.y)
                    moveTo(l.x, l.y)
                    lineTo(mark.to.x, mark.to.y)
                    lineTo(r.x, r.y)
                }
                canvas.drawPath(path, line)
            }
            is Mark.Shape -> {
                line.color = mark.color
                val r = RectF(minOf(mark.from.x, mark.to.x), minOf(mark.from.y, mark.to.y), maxOf(mark.from.x, mark.to.x), maxOf(mark.from.y, mark.to.y))
                if (mark.ellipse) canvas.drawOval(r, line) else canvas.drawRect(r, line)
            }
            is Mark.Label -> {
                text.color = mark.color
                var y = mark.at.y - text.ascent()
                for (row in mark.text.lines()) {
                    canvas.drawText(row, mark.at.x, y, text)
                    y += text.fontSpacing
                }
            }
        }
    }
    return out
}
