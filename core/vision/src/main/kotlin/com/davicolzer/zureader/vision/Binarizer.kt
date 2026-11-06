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

    /**
     * Limiar local (média da vizinhança, por imagem integral): um pixel é tinta se for mais escuro
     * (ou mais claro, se [inkIsDark] for falso) que a média de um quadrado de lado [window] em volta,
     * por pelo menos [contrast] da média e [minDelta] níveis de cinza.
     * Separa glifo de parede mesmo quando o resto da foto (céu, cenário, sombra) é bem diferente.
     */
    fun adaptiveMask(
        image: GrayImage,
        window: Int,
        inkIsDark: Boolean,
        contrast: Float = 0.10f,
        minDelta: Int = 8,
    ): BooleanArray {
        val w = image.width
        val h = image.height
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += image.pixels[y * w + x]
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + row
            }
        }
        val r = (window / 2).coerceAtLeast(1)
        return BooleanArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val x0 = (x - r).coerceAtLeast(0)
            val x1 = (x + r).coerceAtMost(w - 1)
            val y0 = (y - r).coerceAtLeast(0)
            val y1 = (y + r).coerceAtMost(h - 1)
            val sum = integral[(y1 + 1) * (w + 1) + x1 + 1] - integral[y0 * (w + 1) + x1 + 1] -
                integral[(y1 + 1) * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
            val mean = sum.toFloat() / ((x1 - x0 + 1) * (y1 - y0 + 1))
            val p = image.pixels[i]
            val delta = maxOf(minDelta.toFloat(), contrast * mean)
            if (inkIsDark) p < mean - delta else p > mean + delta
        }
    }

    /**
     * Afina a tinta: um pixel continua tinta só se todo o quadrado de raio [radius] em volta for tinta.
     * Quebra pontes finas entre glifos que a foto borrou (o espaço entre eles some no desfoque).
     */
    fun erode(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask
        // conta pixels SEM tinta em cada janela, primeiro na horizontal e depois na vertical
        val rows = BooleanArray(mask.size)
        for (y in 0 until height) {
            val prefix = IntArray(width + 1)
            for (x in 0 until width) prefix[x + 1] = prefix[x] + if (mask[y * width + x]) 0 else 1
            for (x in 0 until width) {
                val a = (x - radius).coerceAtLeast(0)
                val b = (x + radius).coerceAtMost(width - 1)
                rows[y * width + x] = x - radius >= 0 && x + radius < width && prefix[b + 1] - prefix[a] == 0
            }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until width) {
            val prefix = IntArray(height + 1)
            for (y in 0 until height) prefix[y + 1] = prefix[y] + if (rows[y * width + x]) 0 else 1
            for (y in 0 until height) {
                val a = (y - radius).coerceAtLeast(0)
                val b = (y + radius).coerceAtMost(height - 1)
                out[y * width + x] = y - radius >= 0 && y + radius < height && prefix[b + 1] - prefix[a] == 0
            }
        }
        return out
    }

    /** Máscara de tinta: `true` onde há tinta. */
    fun mask(image: GrayImage, threshold: Int, inkIsDark: Boolean): BooleanArray =
        BooleanArray(image.pixels.size) { if (inkIsDark) image.pixels[it] <= threshold else image.pixels[it] > threshold }

    /** Engrossa a tinta: um pixel vira tinta se há tinta no quadrado de raio [radius] em volta. */
    fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask
        val inverse = BooleanArray(mask.size) { !mask[it] }
        val eroded = erode(inverse, width, height, radius)
        return BooleanArray(mask.size) { !eroded[it] }
    }

    /**
     * Preenche o miolo de contornos fechados (letreiros de neon desenham só a borda do glifo).
     * Uma região de fundo fechada que encosta numa única mancha de tinta vira tinta; uma que
     * encosta em várias (ex.: o espaço dentro da moldura de uma placa, com letras dentro) fica.
     */
    fun fillHoles(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val n = mask.size
        val ink = IntArray(n) { -1 }
        val stack = IntArray(n)
        var labels = 0
        // manchas de tinta (vizinhança de 8)
        for (start in 0 until n) {
            if (!mask[start] || ink[start] >= 0) continue
            var top = 0; stack[top++] = start; ink[start] = labels
            while (top > 0) {
                val i = stack[--top]; val x = i % width; val y = i / width
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val j = ny * width + nx
                    if (mask[j] && ink[j] < 0) { ink[j] = labels; stack[top++] = j }
                }
            }
            labels++
        }
        val out = mask.copyOf()
        val seen = BooleanArray(n)
        val region = IntArray(n)
        // regiões de fundo (vizinhança de 4)
        for (start in 0 until n) {
            if (mask[start] || seen[start]) continue
            var top = 0; stack[top++] = start; seen[start] = true
            var size = 0; var border = false; var touched = -1; var many = false
            while (top > 0) {
                val i = stack[--top]; region[size++] = i
                val x = i % width; val y = i / width
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) border = true
                if (x > 0) { val j = i - 1; if (mask[j]) { if (touched < 0) touched = ink[j] else if (touched != ink[j]) many = true } else if (!seen[j]) { seen[j] = true; stack[top++] = j } }
                if (x < width - 1) { val j = i + 1; if (mask[j]) { if (touched < 0) touched = ink[j] else if (touched != ink[j]) many = true } else if (!seen[j]) { seen[j] = true; stack[top++] = j } }
                if (y > 0) { val j = i - width; if (mask[j]) { if (touched < 0) touched = ink[j] else if (touched != ink[j]) many = true } else if (!seen[j]) { seen[j] = true; stack[top++] = j } }
                if (y < height - 1) { val j = i + width; if (mask[j]) { if (touched < 0) touched = ink[j] else if (touched != ink[j]) many = true } else if (!seen[j]) { seen[j] = true; stack[top++] = j } }
            }
            if (!border && !many && touched >= 0) for (k in 0 until size) out[region[k]] = true
        }
        return out
    }
}
