package com.davicolzer.zureader.vision

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class Point(val x: Float, val y: Float)

/** Quadrilátero na imagem, em sentido horário a partir do canto superior esquerdo. */
data class Quad(val topLeft: Point, val topRight: Point, val bottomRight: Point, val bottomLeft: Point) {
    val corners: List<Point> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        fun of(box: PixelBox) = Quad(
            Point(box.left.toFloat(), box.top.toFloat()),
            Point(box.right.toFloat(), box.top.toFloat()),
            Point(box.right.toFloat(), box.bottom.toFloat()),
            Point(box.left.toFloat(), box.bottom.toFloat()),
        )

        /** A imagem inteira, recuada [inset] (fração) de cada lado. */
        fun inset(width: Int, height: Int, inset: Float): Quad {
            val dx = width * inset
            val dy = height * inset
            return Quad(Point(dx, dy), Point(width - dx, dy), Point(width - dx, height - dy), Point(dx, height - dy))
        }
    }
}

/**
 * Corrige perspectiva: transforma o quadrilátero escolhido (por exemplo, o texto numa
 * foto tirada de lado, que fica em forma de trapézio) num retângulo.
 */
object Perspective {

    /** Recorta [quad] de [image] e endireita num retângulo de lado máximo [maxSide]. */
    fun rectify(image: GrayImage, quad: Quad, maxSide: Int = 1600, fill: Int = 255): GrayImage {
        val (tl, tr, br, bl) = quad.corners
        var w = max(distance(tl, tr), distance(bl, br))
        var h = max(distance(tl, bl), distance(tr, br))
        val scale = minOf(1f, maxSide / max(w, h))
        w *= scale; h *= scale
        val outW = w.roundToInt().coerceAtLeast(1)
        val outH = h.roundToInt().coerceAtLeast(1)

        // Para cada pixel do retângulo, onde ele cai na imagem original.
        val m = homography(
            listOf(Point(0f, 0f), Point(outW.toFloat(), 0f), Point(outW.toFloat(), outH.toFloat()), Point(0f, outH.toFloat())),
            quad.corners,
        )
        val out = IntArray(outW * outH)
        for (y in 0 until outH) for (x in 0 until outW) {
            val px = x + 0.5
            val py = y + 0.5
            val d = m[6] * px + m[7] * py + m[8]
            val sx = (m[0] * px + m[1] * py + m[2]) / d - 0.5
            val sy = (m[3] * px + m[4] * py + m[5]) / d - 0.5
            out[y * outW + x] = bilinear(image, sx, sy, fill)
        }
        return GrayImage(outW, outH, out)
    }

    /**
     * Homografia 3×3 (linha a linha, h[8] = 1) que leva cada ponto de [from] ao ponto
     * correspondente de [to]. Resolve o sistema 8×8 por eliminação de Gauss.
     */
    fun homography(from: List<Point>, to: List<Point>): DoubleArray {
        require(from.size == 4 && to.size == 4)
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = from[i].x.toDouble(); val y = from[i].y.toDouble()
            val u = to[i].x.toDouble(); val v = to[i].y.toDouble()
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (col in 0 until 8) {
            val pivot = (col until 8).maxBy { kotlin.math.abs(a[it][col]) }
            require(kotlin.math.abs(a[pivot][col]) > 1e-9) { "Cantos degenerados" }
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            for (r in 0 until 8) {
                if (r == col) continue
                val f = a[r][col] / a[col][col]
                for (c in col..8) a[r][c] -= f * a[col][c]
            }
        }
        return DoubleArray(9) { if (it == 8) 1.0 else a[it][8] / a[it][it] }
    }

    private fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)

    private fun bilinear(img: GrayImage, x: Double, y: Double, fill: Int): Int {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        if (x0 < -1 || y0 < -1 || x0 >= img.width || y0 >= img.height) return fill
        val fx = x - x0
        val fy = y - y0
        fun px(xx: Int, yy: Int) = img[xx.coerceIn(0, img.width - 1), yy.coerceIn(0, img.height - 1)]
        val top = px(x0, y0) * (1 - fx) + px(x0 + 1, y0) * fx
        val bottom = px(x0, y0 + 1) * (1 - fx) + px(x0 + 1, y0 + 1) * fx
        return (top * (1 - fy) + bottom * fy).roundToInt()
    }
}
