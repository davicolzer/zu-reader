package com.davicolzer.zureader.vision

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Recorte retangular (quinas sempre a 90°), em pixels da imagem. A tela de recorte usa as
 * regras daqui: puxar um canto ou uma borda, arrastar o retângulo inteiro ou, tocando fora
 * dele, desenhar um retângulo novo.
 */
data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun toQuad() = Quad(Point(left, top), Point(right, top), Point(right, bottom), Point(left, bottom))

    /** O que o dedo pega ao tocar em ([x], [y]); [touch] é o raio de toque em pixels da imagem. */
    fun hit(x: Float, y: Float, touch: Float): Handle {
        val corners = listOf(Handle.TOP_LEFT to Point(left, top), Handle.TOP_RIGHT to Point(right, top), Handle.BOTTOM_RIGHT to Point(right, bottom), Handle.BOTTOM_LEFT to Point(left, bottom))
        val (corner, distance) = corners.map { (h, p) -> h to hypot(p.x - x, p.y - y) }.minBy { it.second }
        if (distance <= touch) return corner
        val insideX = x in left..right
        val insideY = y in top..bottom
        return when {
            insideY && abs(x - left) <= touch -> Handle.LEFT
            insideY && abs(x - right) <= touch -> Handle.RIGHT
            insideX && abs(y - top) <= touch -> Handle.TOP
            insideX && abs(y - bottom) <= touch -> Handle.BOTTOM
            insideX && insideY -> Handle.MOVE
            else -> Handle.NEW
        }
    }

    /**
     * Arrasta [handle] por ([dx], [dy]) dentro de uma imagem [imageWidth]×[imageHeight].
     * Se um canto passar do outro, o retângulo vira (sem ficar menor que [minSize]).
     */
    fun drag(handle: Handle, dx: Float, dy: Float, imageWidth: Int, imageHeight: Int, minSize: Float = 1f): CropBox {
        if (handle == Handle.MOVE) {
            val mx = dx.coerceIn(-left, imageWidth - right)
            val my = dy.coerceIn(-top, imageHeight - bottom)
            return CropBox(left + mx, top + my, right + mx, bottom + my)
        }
        var l = left; var t = top; var r = right; var b = bottom
        if (handle.left) l += dx
        if (handle.right) r += dx
        if (handle.top) t += dy
        if (handle.bottom) b += dy
        return spanning(Point(l, t), Point(r, b), imageWidth, imageHeight, minSize)
    }

    enum class Handle(val left: Boolean = false, val top: Boolean = false, val right: Boolean = false, val bottom: Boolean = false) {
        TOP_LEFT(left = true, top = true),
        TOP_RIGHT(right = true, top = true),
        BOTTOM_RIGHT(right = true, bottom = true),
        BOTTOM_LEFT(left = true, bottom = true),
        LEFT(left = true),
        TOP(top = true),
        RIGHT(right = true),
        BOTTOM(bottom = true),

        /** Dentro do retângulo: arrasta tudo. */
        MOVE,

        /** Fora do retângulo: desenha um novo, do ponto tocado até o dedo. */
        NEW,
    }

    companion object {
        /** Retângulo entre dois pontos quaisquer, preso à imagem e com lado mínimo [minSize]. */
        fun spanning(a: Point, b: Point, imageWidth: Int, imageHeight: Int, minSize: Float = 1f): CropBox {
            val w = imageWidth.toFloat(); val h = imageHeight.toFloat()
            var l = minOf(a.x, b.x).coerceIn(0f, w); var r = maxOf(a.x, b.x).coerceIn(0f, w)
            var t = minOf(a.y, b.y).coerceIn(0f, h); var bt = maxOf(a.y, b.y).coerceIn(0f, h)
            val min = minSize.coerceAtMost(minOf(w, h))
            if (r - l < min) { if (l + min <= w) r = l + min else l = r - min }
            if (bt - t < min) { if (t + min <= h) bt = t + min else t = bt - min }
            return CropBox(l, t, r, bt)
        }

        /** Menor retângulo que contém o quadrilátero (a sugestão automática). */
        fun around(quad: Quad, imageWidth: Int, imageHeight: Int): CropBox {
            val xs = quad.corners.map { it.x }; val ys = quad.corners.map { it.y }
            return spanning(Point(xs.min(), ys.min()), Point(xs.max(), ys.max()), imageWidth, imageHeight)
        }
    }
}
