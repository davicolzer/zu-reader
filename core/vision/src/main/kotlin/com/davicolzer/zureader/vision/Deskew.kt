package com.davicolzer.zureader.vision

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Máscara de tinta já endireitada, com o caminho de volta para a imagem original. */
class DeskewedMask(
    val mask: BooleanArray,
    val width: Int,
    val height: Int,
    /** Ângulo aplicado para endireitar, em graus (positivo = sentido horário na tela). */
    val angleDegrees: Float,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
) {
    private val radians = Math.toRadians(angleDegrees.toDouble())
    private val cos = cos(radians)
    private val sin = sin(radians)

    /** Converte um ponto da máscara endireitada para a imagem original. */
    fun toSource(x: Float, y: Float): Pair<Float, Float> {
        val dx = x - width / 2.0
        val dy = y - height / 2.0
        // inversa da rotação
        val sx = dx * cos + dy * sin + sourceWidth / 2.0
        val sy = -dx * sin + dy * cos + sourceHeight / 2.0
        return sx.toFloat() to sy.toFloat()
    }
}

/**
 * Corrige inclinação leve. Em texto Zu alinhado, os blocos formam linhas e colunas retas:
 * a projeção da tinta nos dois eixos fica "pontuda" (soma dos quadrados máxima).
 * Testamos ângulos de −[MAX_DEGREES]° a +[MAX_DEGREES]° e giramos a máscara pelo melhor.
 */
object Deskew {
    const val MAX_DEGREES = 12f
    private const val STEP = 0.5f
    private const val MIN_CORRECTION = 0.75f
    private const val MAX_POINTS = 40_000

    fun estimate(mask: BooleanArray, width: Int, height: Int): Float {
        val ink = mask.count { it }
        if (ink < 50) return 0f
        val stride = (ink / MAX_POINTS).coerceAtLeast(1)
        val xs = FloatArray(ink / stride + 1)
        val ys = FloatArray(xs.size)
        var n = 0
        var seen = 0
        for (i in mask.indices) {
            if (!mask[i]) continue
            if (seen++ % stride != 0 || n >= xs.size) continue
            xs[n] = (i % width - width / 2f)
            ys[n] = (i / width - height / 2f)
            n++
        }
        val diagonal = kotlin.math.hypot(width.toDouble(), height.toDouble()).toInt() + 2
        val binsX = IntArray(diagonal)
        val binsY = IntArray(diagonal)

        var best = 0f
        var bestScore = -1L
        var angle = -MAX_DEGREES
        while (angle <= MAX_DEGREES + 1e-3f) {
            val r = Math.toRadians(angle.toDouble())
            val c = cos(r).toFloat()
            val s = sin(r).toFloat()
            binsX.fill(0); binsY.fill(0)
            for (k in 0 until n) {
                val u = (xs[k] * c - ys[k] * s + diagonal / 2).toInt()
                val v = (xs[k] * s + ys[k] * c + diagonal / 2).toInt()
                if (u in 0 until diagonal) binsX[u]++
                if (v in 0 until diagonal) binsY[v]++
            }
            var score = 0L
            for (b in 0 until diagonal) score += binsX[b].toLong() * binsX[b] + binsY[b].toLong() * binsY[b]
            // empate: fica com o ângulo mais perto de zero
            if (score > bestScore || (score == bestScore && abs(angle) < abs(best))) {
                bestScore = score
                best = angle
            }
            angle += STEP
        }
        return if (abs(best) < MIN_CORRECTION) 0f else best
    }

    /** Gira a máscara por [angleDegrees] (vizinho mais próximo), ampliando o quadro para não cortar. */
    fun apply(mask: BooleanArray, width: Int, height: Int, angleDegrees: Float): DeskewedMask {
        if (angleDegrees == 0f) return DeskewedMask(mask, width, height, 0f, width, height)
        val r = Math.toRadians(angleDegrees.toDouble())
        val c = cos(r)
        val s = sin(r)
        val newW = (abs(width * c) + abs(height * s)).roundToInt()
        val newH = (abs(width * s) + abs(height * c)).roundToInt()
        val out = BooleanArray(newW * newH)
        for (y in 0 until newH) for (x in 0 until newW) {
            val dx = x - newW / 2.0
            val dy = y - newH / 2.0
            val sx = (dx * c + dy * s + width / 2.0).toInt()
            val sy = (-dx * s + dy * c + height / 2.0).toInt()
            if (sx in 0 until width && sy in 0 until height) out[y * newW + x] = mask[sy * width + sx]
        }
        return DeskewedMask(out, newW, newH, angleDegrees, width, height)
    }
}
