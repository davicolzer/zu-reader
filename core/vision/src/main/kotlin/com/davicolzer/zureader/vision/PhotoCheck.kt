package com.davicolzer.zureader.vision

import kotlin.math.abs

/** Qualidade do visor para tirar a foto, em 3 níveis. */
enum class PhotoQuality { GOOD, FAIR, POOR }

/** Medidas de um quadro do visor. */
data class FrameStats(val contrast: Int, val sharpness: Float, val motion: Float)

/**
 * Diz se a imagem do visor está boa para fotografar, sem tentar ler os glifos: olha o
 * contraste (dá para separar desenho e fundo?), a nitidez das bordas (está em foco?) e a
 * diferença para o quadro anterior (o celular está tremendo?). O nível final é a mediana
 * dos últimos quadros, para a moldura não piscar. Cada câmera usa a sua instância.
 */
class PhotoCheck {
    private var previous: IntArray? = null
    private val recent = ArrayDeque<PhotoQuality>()

    fun check(frame: GrayImage): PhotoQuality {
        recent.addLast(quality(measure(frame)))
        while (recent.size > SMOOTHING) recent.removeFirst()
        return recent.sortedBy { it.ordinal }[recent.size / 2]
    }

    fun measure(frame: GrayImage): FrameStats {
        val thumb = thumbnail(frame)
        val motion = previous?.let { prev -> thumb.indices.sumOf { abs(thumb[it] - prev[it]) }.toFloat() / thumb.size } ?: 0f
        previous = thumb
        val (contrast, sharpness) = contrastAndSharpness(frame)
        return FrameStats(contrast, sharpness, motion)
    }

    companion object {
        private const val SMOOTHING = 3

        /** Maior lado do quadro analisado (a nitidez foi calibrada nesse tamanho). */
        const val FRAME_SIDE = 320

        /** Contraste (tons 0–255 entre o 2º e o 98º percentil) abaixo do qual não dá para ler. */
        const val POOR_CONTRAST = 30
        const val FAIR_CONTRAST = 60

        /** Nitidez (bordas mais íngremes / contraste, 0–100), medida em quadros de [FRAME_SIDE] px. */
        const val POOR_SHARPNESS = 27f
        const val FAIR_SHARPNESS = 38f

        /** Tremida (diferença média entre quadros seguidos, 0–255). */
        const val POOR_MOTION = 12f
        const val FAIR_MOTION = 5f

        fun quality(s: FrameStats): PhotoQuality = when {
            s.contrast < POOR_CONTRAST || s.sharpness < POOR_SHARPNESS || s.motion > POOR_MOTION -> PhotoQuality.POOR
            s.contrast < FAIR_CONTRAST || s.sharpness < FAIR_SHARPNESS || s.motion > FAIR_MOTION -> PhotoQuality.FAIR
            else -> PhotoQuality.GOOD
        }

        /**
         * No centro da imagem (onde fica a guia): contraste entre os percentis 2 e 98, e nitidez =
         * percentil 98 do degrau entre pixels a 2 de distância, dividido pelo contraste (assim
         * "pouco contraste" não vira "desfocado", e uma cena escura com letreiro claro conta como boa).
         */
        fun contrastAndSharpness(image: GrayImage): Pair<Int, Float> {
            val x0 = image.width / 8
            val x1 = image.width - x0
            val y0 = image.height / 8
            val y1 = image.height - y0
            if (x1 - x0 < 8 || y1 - y0 < 8) return 0 to 0f
            val tones = IntArray(256)
            val steps = IntArray(256)
            for (y in y0 until y1 - 2) for (x in x0 until x1 - 2) {
                val p = image[x, y]
                tones[p]++
                steps[maxOf(abs(image[x + 2, y] - p), abs(image[x, y + 2] - p)).coerceAtMost(255)]++
            }
            val contrast = percentile(tones, 0.98) - percentile(tones, 0.02)
            val sharpness = percentile(steps, 0.98) * 100f / contrast.coerceAtLeast(1)
            return contrast to sharpness.coerceAtMost(100f)
        }

        private fun percentile(histogram: IntArray, fraction: Double): Int {
            val target = histogram.sum() * fraction
            var seen = 0L
            for (i in histogram.indices) {
                seen += histogram[i]
                if (seen >= target) return i
            }
            return histogram.lastIndex
        }

        private fun thumbnail(image: GrayImage): IntArray = IntArray(THUMB * THUMB) { i ->
            val tx = i % THUMB
            val ty = i / THUMB
            val xs = tx * image.width / THUMB
            val xe = ((tx + 1) * image.width / THUMB).coerceAtLeast(xs + 1)
            val ys = ty * image.height / THUMB
            val ye = ((ty + 1) * image.height / THUMB).coerceAtLeast(ys + 1)
            var sum = 0L
            for (y in ys until ye) for (x in xs until xe) sum += image[x, y]
            (sum / ((xe - xs) * (ye - ys))).toInt()
        }

        private const val THUMB = 32
    }
}
