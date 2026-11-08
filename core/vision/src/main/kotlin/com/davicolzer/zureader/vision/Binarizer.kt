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
     * Letreiros de neon: cada glifo é o contorno da sua área de tinta, e glifos com buraco têm um
     * contorno por fora e outro por dentro. Pinta pela profundidade: o fundo de fora tem
     * profundidade 0; cruzar um contorno soma 1. Profundidade ímpar = dentro da tinta (pinta);
     * par = fundo ou buraco do glifo. Antes, tira as manchas bem maiores que um glifo (a moldura
     * da placa), que deixariam a placa inteira "dentro".
     */
    fun fillOutlines(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val n = mask.size
        val stack = IntArray(n)
        // 1. manchas de tinta (vizinhança de 8) e o tamanho de cada uma
        val ink = IntArray(n) { -1 }
        val sides = ArrayList<Int>()
        for (start in 0 until n) {
            if (!mask[start] || ink[start] >= 0) continue
            val label = sides.size
            var top = 0; stack[top++] = start; ink[start] = label
            var minX = width; var minY = height; var maxX = -1; var maxY = -1
            while (top > 0) {
                val i = stack[--top]; val x = i % width; val y = i / width
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val j = ny * width + nx
                    if (mask[j] && ink[j] < 0) { ink[j] = label; stack[top++] = j }
                }
            }
            sides += maxOf(maxX - minX, maxY - minY) + 1
        }
        if (sides.isEmpty()) return mask
        val glyphLike = sides.filter { it >= MIN_OUTLINE_SIDE }.sorted()
        val typical = if (glyphLike.isEmpty()) Int.MAX_VALUE else glyphLike[glyphLike.size / 2]
        val frame = BooleanArray(sides.size) { sides[it] > typical * FRAME_RATIO }
        val work = BooleanArray(n) { mask[it] && !frame[ink[it]] }

        // 2. regiões de fundo (vizinhança de 4) e as manchas que cada uma toca
        val region = IntArray(n) { -1 }
        val touches = ArrayList<MutableSet<Int>>()
        val outer = ArrayList<Boolean>()
        for (start in 0 until n) {
            if (work[start] || region[start] >= 0) continue
            val label = touches.size
            val adjacent = HashSet<Int>()
            var border = false
            var top = 0; stack[top++] = start; region[start] = label
            while (top > 0) {
                val i = stack[--top]; val x = i % width; val y = i / width
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) border = true
                fun visit(j: Int) {
                    if (work[j]) adjacent += ink[j] else if (region[j] < 0) { region[j] = label; stack[top++] = j }
                }
                if (x > 0) visit(i - 1)
                if (x < width - 1) visit(i + 1)
                if (y > 0) visit(i - width)
                if (y < height - 1) visit(i + width)
            }
            touches += adjacent; outer += border
        }
        // 3. profundidade: das regiões da borda, atravessando uma mancha por vez
        val regionsOf = HashMap<Int, MutableList<Int>>()
        touches.forEachIndexed { r, comps -> for (c in comps) regionsOf.getOrPut(c) { ArrayList() } += r }
        val depth = IntArray(touches.size) { -1 }
        val queue = ArrayDeque<Int>()
        for (r in touches.indices) if (outer[r]) { depth[r] = 0; queue += r }
        val crossed = HashSet<Int>()
        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            for (c in touches[r]) {
                if (!crossed.add(c)) continue
                for (r2 in regionsOf[c].orEmpty()) if (depth[r2] < 0) { depth[r2] = depth[r] + 1; queue += r2 }
            }
        }
        return BooleanArray(n) { i -> work[i] || (region[i] >= 0 && depth[region[i]] % 2 == 1) }
    }

    /** Menor lado (px) de uma mancha para contar como contorno de glifo ao medir o tamanho típico. */
    private const val MIN_OUTLINE_SIDE = 8

    /** Mancha com lado acima disto × o típico é moldura de placa, não glifo. */
    private const val FRAME_RATIO = 2.5f
}
