package com.davicolzer.zureader.ui.note

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset

/** Ferramentas do editor de anotações. */
enum class NoteTool { PEN, RECTANGLE, ELLIPSE, TEXT }

/**
 * Um desenho sobre a foto, em pixels da imagem (não da tela), para sair igual na foto salva.
 * [color] em ARGB.
 */
sealed interface Mark {
    val color: Int

    /** Risco à mão livre. */
    data class Stroke(val points: List<Offset>, override val color: Int) : Mark

    /** Retângulo ou círculo (elipse) entre dois cantos. */
    data class Shape(val from: Offset, val to: Offset, val ellipse: Boolean, override val color: Int) : Mark

    /** Texto escrito a partir de [at] (canto de cima à esquerda). */
    data class Label(val at: Offset, val text: String, override val color: Int) : Mark
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
            is Mark.Stroke -> {
                line.color = mark.color
                val path = Path()
                mark.points.firstOrNull()?.let { path.moveTo(it.x, it.y) }
                mark.points.drop(1).forEach { path.lineTo(it.x, it.y) }
                if (mark.points.size == 1) path.lineTo(mark.points[0].x + 0.1f, mark.points[0].y)
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
