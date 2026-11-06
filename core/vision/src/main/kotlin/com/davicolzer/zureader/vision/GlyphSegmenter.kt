package com.davicolzer.zureader.vision

/**
 * Junta as manchas de tinta em caixas de glifo.
 *
 * A maioria dos glifos Zu tem duas peças separadas por um vão de 1 bloco, do mesmo
 * tamanho do espaço entre glifos; por isso não dá para juntar peças pela distância.
 * Mas todo glifo tem tinta em todas as linhas e colunas da sua grade 5×5: a "sombra"
 * das suas peças em cada eixo é um intervalo contínuo do tamanho do glifo.
 *
 * Então cortamos em faixas por um eixo (colunas de texto) e, dentro de cada faixa,
 * pelo outro (glifos). Testamos as duas ordens e ficamos com a que gera mais caixas
 * quadradas do mesmo tamanho.
 */
object GlyphSegmenter {
    private const val MIN_AREA = 4

    fun segment(components: List<PixelBox>): List<PixelBox> {
        val pieces = components.filter { it.area >= MIN_AREA }
        if (pieces.isEmpty()) return emptyList()

        val glyphSize = estimatePieceSize(pieces)
        val block = glyphSize / 5f

        // Tira sujeira e manchas grandes demais (molduras, cenário) e corta.
        val clean = pieces.filter { it.size <= glyphSize * 1.3f && it.area >= 0.25f * block * block }
        val tolerance = (0.3f * block).toInt().coerceAtLeast(1)
        val boxes = bestCut(clean, tolerance)
        val size = estimateSize(boxes) ?: return emptyList()
        return boxes.filter { isSquare(it) && it.size in (size * 0.75f).toInt()..(size * 1.3f).toInt() }
    }

    /**
     * Lado provável do glifo a partir das peças: a peça principal de cada glifo ocupa
     * a largura ou a altura inteira, e as peças soltas têm 1–2 blocos. A mediana das
     * peças grandes ignora tanto as soltas quanto molduras e manchas enormes.
     */
    private fun estimatePieceSize(pieces: List<PixelBox>): Int {
        val sizes = pieces.map { it.size }.sorted()
        val rough = sizes[sizes.size / 2]
        val big = sizes.filter { it >= rough * 0.6f && it <= rough * 3 }
        return big[big.size / 2]
    }

    private fun bestCut(pieces: List<PixelBox>, tolerance: Int): List<PixelBox> {
        val xFirst = cut(pieces, tolerance, xFirst = true)
        val yFirst = cut(pieces, tolerance, xFirst = false)
        return if (score(yFirst) > score(xFirst)) yFirst else xFirst
    }

    private fun cut(pieces: List<PixelBox>, tolerance: Int, xFirst: Boolean): List<PixelBox> {
        val first: (PixelBox) -> IntRange = if (xFirst) { b -> b.left until b.right } else { b -> b.top until b.bottom }
        val second: (PixelBox) -> IntRange = if (xFirst) { b -> b.top until b.bottom } else { b -> b.left until b.right }
        return group(pieces, first, tolerance).flatMap { strip ->
            group(strip, second, tolerance).map { it.reduce(PixelBox::union) }
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

    private fun isSquare(b: PixelBox): Boolean = b.width >= b.height * 0.8f && b.height >= b.width * 0.8f

    private fun estimateSize(boxes: List<PixelBox>): Int? {
        val sizes = boxes.filter(::isSquare).map { it.size }.sorted()
        return sizes.getOrNull(sizes.size / 2)
    }

    private fun score(boxes: List<PixelBox>): Int {
        val size = estimateSize(boxes) ?: return 0
        return boxes.count { isSquare(it) && it.size in (size * 0.85f).toInt()..(size * 1.15f).toInt() + 1 }
    }
}
