package com.davicolzer.zureader.vision

/**
 * Junta as manchas de tinta em caixas de glifo, agrupadas por tamanho.
 *
 * A maioria dos glifos Zu tem duas peças separadas por um vão de 1 bloco, do mesmo
 * tamanho do espaço entre glifos; por isso não dá para juntar peças pela distância.
 * Mas todo glifo tem tinta em todas as linhas e colunas da sua grade 5×5: a "sombra"
 * das suas peças em cada eixo é um intervalo contínuo do tamanho do glifo.
 *
 * Então cortamos em faixas por um eixo (colunas de texto) e, dentro de cada faixa,
 * pelo outro (glifos). Testamos as duas ordens e ficamos com a que gera mais caixas
 * do mesmo tamanho. Largura e altura são medidas separadamente, então glifos
 * esticados ou achatados também servem.
 *
 * Textos com tamanhos diferentes (ex.: título e corpo) viram grupos separados.
 */
object GlyphSegmenter {
    private const val MIN_AREA = 4
    private const val MAX_GROUPS = 4

    /** Menor lado de glifo legível: 2 px por bloco. */
    const val MIN_GLYPH_PX = 10

    /** Lista de grupos; cada grupo tem glifos do mesmo tamanho. */
    fun segmentGroups(components: List<PixelBox>): List<List<PixelBox>> {
        var remaining = removeOutliers(components.filter { it.area >= MIN_AREA })
        val groups = mutableListOf<List<PixelBox>>()
        while (remaining.isNotEmpty() && groups.size < MAX_GROUPS) {
            val (w, h) = dominantSize(bestCut(remaining, 1, 1, null)) ?: break
            val boxes = segmentWithSize(remaining, w, h)
            // um grupo extra de 1 glifo costuma ser sujeira do cenário
            if (boxes.isEmpty() || (groups.isNotEmpty() && boxes.size < 2)) break
            groups += boxes
            remaining = remaining.filterNot { piece -> boxes.any { it.contains(piece) } }
        }
        return groups
    }

    /** Todos os glifos, sem separar por tamanho. */
    fun segment(components: List<PixelBox>): List<PixelBox> = segmentGroups(components).flatten()

    private fun segmentWithSize(pieces: List<PixelBox>, width: Int, height: Int): List<PixelBox> {
        val blockW = width / 5f
        val blockH = height / 5f
        val fitting = pieces.filter {
            it.width <= width * 1.3f && it.height <= height * 1.3f && it.area >= 0.25f * blockW * blockH
        }
        val tolX = (0.3f * blockW).toInt().coerceAtLeast(1)
        val tolY = (0.3f * blockH).toInt().coerceAtLeast(1)
        return bestCut(fitting, tolX, tolY, width to height).filter {
            matches(it, width, height, 0.75f, 1.3f) && it.width >= MIN_GLYPH_PX && it.height >= MIN_GLYPH_PX
        }
    }

    /** Descarta molduras e manchas enormes antes de medir. */
    private fun removeOutliers(pieces: List<PixelBox>): List<PixelBox> {
        if (pieces.isEmpty()) return pieces
        val rough = pieces.map { it.size }.sorted()[pieces.size / 2]
        return pieces.filter { it.size <= rough * 4 }
    }

    /**
     * Tamanho (largura, altura) mais comum entre as caixas: para cada caixa, conta quantas
     * têm largura e altura parecidas; vence a que tiver mais vizinhas.
     */
    private fun dominantSize(boxes: List<PixelBox>): Pair<Int, Int>? {
        if (boxes.isEmpty()) return null
        val best = boxes.maxBy { b -> boxes.count { matches(it, b.width, b.height, 0.8f, 1.25f) } * 10_000L + b.area }
        val similar = boxes.filter { matches(it, best.width, best.height, 0.8f, 1.25f) }
        val w = similar.map { it.width }.sorted()[similar.size / 2]
        val h = similar.map { it.height }.sorted()[similar.size / 2]
        return w to h
    }

    private fun matches(b: PixelBox, width: Int, height: Int, low: Float, high: Float): Boolean =
        b.width >= width * low && b.width <= width * high + 1 && b.height >= height * low && b.height <= height * high + 1

    private fun bestCut(pieces: List<PixelBox>, tolX: Int, tolY: Int, size: Pair<Int, Int>?): List<PixelBox> {
        val xFirst = cut(pieces, tolX, tolY, xFirst = true)
        val yFirst = cut(pieces, tolX, tolY, xFirst = false)
        return if (score(yFirst, size) > score(xFirst, size)) yFirst else xFirst
    }

    private fun cut(pieces: List<PixelBox>, tolX: Int, tolY: Int, xFirst: Boolean): List<PixelBox> {
        val xRange: (PixelBox) -> IntRange = { b -> b.left until b.right }
        val yRange: (PixelBox) -> IntRange = { b -> b.top until b.bottom }
        val (first, firstTol) = if (xFirst) xRange to tolX else yRange to tolY
        val (second, secondTol) = if (xFirst) yRange to tolY else xRange to tolX
        return group(pieces, first, firstTol).flatMap { strip ->
            group(strip, second, secondTol).map { it.reduce(PixelBox::union) }
        }
    }

    /** Agrupa peças cujas projeções no eixo se sobrepõem ou ficam a até [tolerance] pixels. */
    private fun group(pieces: List<PixelBox>, range: (PixelBox) -> IntRange, tolerance: Int): List<List<PixelBox>> {
        val groups = mutableListOf<MutableList<PixelBox>>()
        var end = Int.MIN_VALUE
        for (p in pieces.sortedBy { range(it).first }) {
            val r = range(p)
            if (groups.isEmpty() || r.first > end + tolerance) {
                groups += mutableListOf(p)
                end = r.last + 1
            } else {
                groups.last() += p
                end = maxOf(end, r.last + 1)
            }
        }
        return groups
    }

    private fun score(boxes: List<PixelBox>, size: Pair<Int, Int>?): Int {
        val (w, h) = size ?: dominantSize(boxes) ?: return 0
        return boxes.count { matches(it, w, h, 0.85f, 1.15f) }
    }
}
