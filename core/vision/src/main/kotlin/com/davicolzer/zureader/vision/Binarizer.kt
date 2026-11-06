package com.davicolzer.zureader.vision

/** Separa tinta e fundo. */
object Binarizer {
    /** Limiar de Otsu: pixels com valor ≤ limiar ficam na classe escura. */
    fun otsu(image: GrayImage): Int {
        val histogram = IntArray(256)
        for (p in image.pixels) histogram[p.coerceIn(0, 255)]++
        val total = image.pixels.size.toDouble()
        val sumAll = (0..255).sumOf { it.toDouble() * histogram[it] }

        var bestThreshold = 127
        var bestVariance = -1.0
        var weightDark = 0.0
        var sumDark = 0.0
        for (t in 0..254) {
            weightDark += histogram[t]
            if (weightDark == 0.0) continue
            val weightLight = total - weightDark
            if (weightLight == 0.0) break
            sumDark += t.toDouble() * histogram[t]
            val meanDark = sumDark / weightDark
            val meanLight = (sumAll - sumDark) / weightLight
            val variance = weightDark * weightLight * (meanDark - meanLight) * (meanDark - meanLight)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = t
            }
        }
        return bestThreshold
    }

    /** Máscara de tinta: `true` onde há tinta. */
    fun mask(image: GrayImage, threshold: Int, inkIsDark: Boolean): BooleanArray =
        BooleanArray(image.pixels.size) { if (inkIsDark) image.pixels[it] <= threshold else image.pixels[it] > threshold }
}
