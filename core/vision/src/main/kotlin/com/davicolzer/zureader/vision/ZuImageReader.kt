package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.GlyphClassifier
import com.davicolzer.zureader.zu.PlacedGlyph
import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuGlyph
import com.davicolzer.zureader.zu.ZuLayout
import com.davicolzer.zureader.zu.ZuReader
import com.davicolzer.zureader.zu.ZuReading

/** Nenhum glifo Zu é todo preenchido; um quadrado sólido é parede, objeto ou sujeira. */
private fun PlacedGlyph.inkCells(): Int {
    var filled = 0
    for (r in 0 until bits.size) for (c in 0 until bits.size) if (bits[r, c]) filled++
    return filled
}

private fun PlacedGlyph.isSolid(): Boolean {
    var filled = 0
    for (r in 0 until bits.size) for (c in 0 until bits.size) if (bits[r, c]) filled++
    return filled >= bits.size * bits.size - 1
}

/** Resultado da leitura de uma imagem. */
data class ImageReading(
    /** Todos os blocos juntos; o texto separa blocos com quebra de linha. Posições na imagem original. */
    val reading: ZuReading,
    /** Um bloco por tamanho de glifo (ex.: título e corpo), na ordem de leitura. */
    val blocks: List<ZuReading>,
    /** `true` se o texto é escuro sobre fundo claro. */
    val inkIsDark: Boolean,
    val width: Int,
    val height: Int,
    /** Inclinação do texto na imagem, em graus (positivo = sentido horário). */
    val skewDegrees: Float = 0f,
    /** Escrita ou números. */
    val mode: ReadingMode = ReadingMode.TEXT,
) {
    /** Nota da leitura: glifos confiantes somam, duvidosos subtraem (compara leituras da mesma imagem). */
    val quality: Float get() = reading.glyphs.sumOf { it.confidence.toDouble() - ZuImageReader.QUALITY_OFFSET }.toFloat()

    /**
     * Recorte sugerido: a área do texto com [margin] glifo de folga em volta,
     * limitada à imagem. `null` se nada foi lido.
     */
    fun suggestedCrop(margin: Float = 0.75f): Quad? {
        val glyphs = reading.glyphs
        if (glyphs.isEmpty()) return null
        val pad = glyphs.map { it.source.size }.sorted()[glyphs.size / 2] * margin
        val left = (glyphs.minOf { it.source.x - it.source.width / 2 } - pad).coerceAtLeast(0f)
        val top = (glyphs.minOf { it.source.y - it.source.height / 2 } - pad).coerceAtLeast(0f)
        val right = (glyphs.maxOf { it.source.x + it.source.width / 2 } + pad).coerceAtMost(width.toFloat())
        val bottom = (glyphs.maxOf { it.source.y + it.source.height / 2 } + pad).coerceAtMost(height.toFloat())
        return Quad(Point(left, top), Point(right, top), Point(right, bottom), Point(left, bottom))
    }
}

/**
 * Imagem → texto: limiar de Otsu, correção de inclinação, manchas de tinta, caixas de glifo
 * (agrupadas por tamanho), grade 5×5 de cada glifo e [ZuReader].
 * Testa texto escuro e texto claro e fica com o que encontra mais glifos confiáveis.
 */
class ZuImageReader(
    private val reader: ZuReader = ZuReader(),
    private val classifier: GlyphClassifier = GlyphClassifier(),
    private val mode: ReadingMode = ReadingMode.TEXT,
) : ImageTextReader {
    /**
     * @param deskew corrigir inclinação.
     * @param thorough testar todas as máscaras. Desligar (com [deskew] desligado) deixa a leitura
     * bem mais rápida, para o visor ao vivo da câmera.
     */
    override fun read(image: GrayImage, deskew: Boolean, thorough: Boolean): ImageReading =
        choose(image, listOf(candidates(image, deskew, if (thorough) ADAPTIVE_WINDOWS else QUICK_WINDOWS)))

    /**
     * Lê uma imagem colorida. Primeiro em cinza (luminância); se a leitura sair fraca, testa também
     * contraste esticado, cada canal de cor e saturação, e combina os glifos de todas as versões.
     */
    override fun read(image: RgbImage, deskew: Boolean, thorough: Boolean): ImageReading {
        val luma = image.luma()
        val windows = if (thorough) ADAPTIVE_WINDOWS else QUICK_WINDOWS
        val first = candidates(luma, deskew, windows)
        if (!thorough || !isWeak(first)) return choose(luma, listOf(first))
        // Segunda etapa: máscaras afinadas (separam glifos que a foto juntou) e versões de cor.
        val thinned = ERODE_RADII.map { r -> candidates(luma, deskew = false, windows = ERODE_WINDOWS, erosion = r) }
        val variants = listOf(luma.stretched(), image.red(), image.green(), image.blue(), image.saturation())
        return choose(luma, listOf(first) + thinned + variants.map { candidates(it, deskew = false, windows = QUICK_WINDOWS) })
    }

    /** Leitura fraca: poucos glifos ou confiança média baixa no melhor candidato. */
    private fun isWeak(candidates: List<Candidate>): Boolean {
        val glyphs = candidates.maxBy { it.quality }.reading.reading.glyphs
        return glyphs.size < WEAK_GLYPHS || glyphs.map { it.confidence }.average() < WEAK_CONFIDENCE
    }

    /**
     * Uma leitura candidata por máscara: limiar único e limiar local em várias escalas, nas duas polaridades.
     * Com [erosion] > 0, os glifos são separados numa cópia afinada da máscara (ver [Binarizer.erode]).
     */
    private fun candidates(image: GrayImage, deskew: Boolean, windows: List<Float>, erosion: Int = 0): List<Candidate> {
        val threshold = Binarizer.otsu(image)
        val masks = buildList {
            // limiar único: bom para prints limpos e recortes só com a parede
            for (dark in listOf(true, false)) add(Binarizer.mask(image, threshold, dark) to dark)
            // limiar local, em algumas escalas: bom para fotos com cenário em volta.
            // Antes, um desfoque leve tira o chiado de foto de tela (moiré) e de JPEG.
            // A janela ideal tem uns 3–4 glifos; como o tamanho do glifo não é conhecido, testa várias
            // (num recorte o texto ocupa a imagem quase toda; numa foto inteira, uma parte pequena).
            val side = maxOf(image.width, image.height)
            val blurred = HashMap<Int, GrayImage>()
            for (fraction in windows) {
                val window = (side * fraction).toInt()
                if (window < 9) continue
                val radius = (window / BLUR_PER_WINDOW).coerceAtLeast(1)
                val smooth = blurred.getOrPut(radius) { image.boxBlur(radius) }
                for (dark in listOf(true, false)) add(Binarizer.adaptiveMask(smooth, window, dark) to dark)
            }
        }
        return masks.map { (mask, dark) -> readWithMask(image, mask, dark, deskew, erosion) }
    }

    /**
     * Escolhe entre os candidatos e a combinação deles. Cada máscara (e cada versão da imagem)
     * acerta glifos diferentes: a combinação junta, glifo a glifo, o melhor de cada posição.
     * Em cada versão ([perVariant]) só entram as máscaras da polaridade do seu melhor candidato:
     * a polaridade invertida enxerga o fundo como tinta e traz lixo. Entre versões a polaridade
     * pode mudar (na saturação, o glifo pode ser o mais claro).
     */
    private fun choose(image: GrayImage, perVariant: List<List<Candidate>>): ImageReading {
        val candidates = perVariant.flatten()
        val best = candidates.maxBy { it.quality }
        val fused = fuse(
            perVariant.flatMap { variant ->
                val ink = variant.maxBy { it.quality }.reading.inkIsDark
                variant.filter { it.reading.inkIsDark == ink }.map { it.straightGlyphs }
            },
        )
        val fusedReading = if (fused.isEmpty()) null else readGroups(image, groupBySize(fused), identity(image), best.reading.inkIsDark).let { it.first to it.second }
        val all = candidates.map { it.reading to it.quality } + listOfNotNull(fusedReading)
        return all.maxBy { it.second }.first
    }

    private class Candidate(val reading: ImageReading, val quality: Float, val straightGlyphs: List<PlacedGlyph>)

    private fun identity(image: GrayImage) = DeskewedMask(BooleanArray(0), image.width, image.height, 0f, image.width, image.height)

    /**
     * Junta os glifos de várias máscaras: em cada posição fica o mais confiante. Um glifo só entra
     * se apareceu em pelo menos [MIN_VOTES] máscaras ou se for bem confiante, porque sujeira do
     * cenário costuma aparecer numa máscara só.
     */
    private fun fuse(sets: List<List<PlacedGlyph>>): List<PlacedGlyph> {
        class Cell(val glyph: PlacedGlyph, val confidence: Float) { val votes = mutableSetOf<Int>() }
        val all = sets.flatMapIndexed { i, set -> set.map { Triple(i, it, classifier.classify(it.bits).confidence) } }
            .sortedByDescending { it.third }
        val cells = mutableListOf<Cell>()
        for ((set, glyph, confidence) in all) {
            val same = cells.firstOrNull { c ->
                val near = minOf(c.glyph.size, glyph.size) / 2
                kotlin.math.abs(c.glyph.x - glyph.x) < near && kotlin.math.abs(c.glyph.y - glyph.y) < near
            }
            (same ?: Cell(glyph, confidence).also { cells += it }).votes += set
        }
        return cells.filter { it.votes.size >= MIN_VOTES || it.confidence >= SURE_CONFIDENCE }.map { it.glyph }
    }

    /** Agrupa glifos de tamanho parecido que se encostam (um grupo = um bloco de texto). */
    private fun groupBySize(glyphs: List<PlacedGlyph>): List<List<PlacedGlyph>> {
        val parent = IntArray(glyphs.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        for (i in glyphs.indices) for (j in i + 1 until glyphs.size) {
            val a = glyphs[i]
            val b = glyphs[j]
            if (maxOf(a.size, b.size) / minOf(a.size, b.size) > MERGE_SIZE_RATIO) continue
            val pad = maxOf(a.size, b.size) * MERGE_GAP / 2
            if (bounds(listOf(a), pad).intersects(bounds(listOf(b), pad))) parent[find(i)] = find(j)
        }
        return glyphs.indices.groupBy(::find).values.map { idx -> idx.map { glyphs[it] } }
    }

    private fun readWithMask(image: GrayImage, raw: BooleanArray, inkIsDark: Boolean, deskew: Boolean, erosion: Int = 0): Candidate {
        // Endireitar só pode ajudar: lê como veio e endireitado, e fica com o melhor.
        val angle = if (deskew) Deskew.estimate(raw, image.width, image.height) else 0f
        val straightMask = Deskew.apply(raw, image.width, image.height, 0f)
        val straightGroups = extractGroups(straightMask, erosion)
        val straight = readGroups(image, straightGroups, straightMask, inkIsDark)
        // glifos (com as vagas completadas) para a combinação entre máscaras
        val glyphs = straight.third.flatten()
        if (angle == 0f) return Candidate(straight.first, straight.second, glyphs)
        val tiltedMask = Deskew.apply(raw, image.width, image.height, angle)
        val tilted = readGroups(image, extractGroups(tiltedMask, erosion), tiltedMask, inkIsDark)
        val chosen = if (tilted.second > straight.second) tilted else straight
        return Candidate(chosen.first, chosen.second, glyphs)
    }

    /**
     * Glifos de uma máscara, agrupados por tamanho (coordenadas da máscara endireitada).
     * Com [erosion] > 0, as caixas saem da máscara afinada e são ampliadas de volta; a grade 5×5
     * de cada glifo é sempre lida na máscara original.
     */
    private fun extractGroups(deskewed: DeskewedMask, erosion: Int = 0): List<List<PlacedGlyph>> {
        val w = deskewed.width
        val h = deskewed.height
        val segmentation = Binarizer.erode(deskewed.mask, w, h, erosion)
        val components = Components.find(segmentation, w, h)
        return GlyphSegmenter.segmentGroups(components)
            .map { group ->
                group.map { b ->
                    PixelBox((b.left - erosion).coerceAtLeast(0), (b.top - erosion).coerceAtLeast(0), (b.right + erosion).coerceAtMost(w), (b.bottom + erosion).coerceAtMost(h))
                }
            }
            .map { boxes ->
                boxes.mapNotNull { box ->
                    val sampled = GlyphSampler.sampleWithQuality(deskewed.mask, deskewed.width, box)
                    if (sampled.crispness < GlyphSampler.MIN_CRISPNESS) return@mapNotNull null
                    PlacedGlyph(box.centerX, box.centerY, box.width.toFloat(), box.height.toFloat(), sampled.bits)
                }.filter { !it.isSolid() && classifier.classify(it.bits).isGlyph }
            }
            .filter { it.isNotEmpty() }
            .let(::mergeNearbyGroups)
    }

    /**
     * Completa vagas da grade: o texto Zu é uma grade regular, então dois glifos alinhados a 2 ou 3
     * passos de distância deixam 1 ou 2 vagas no meio. Uma vaga vazia é espaço entre palavras; uma
     * vaga com tinta é um glifo que a segmentação perdeu (encostado no vizinho ou borrado). Lê a
     * grade 5×5 ali, ajustando a posição um pouco, e fica com ela se for um glifo do alfabeto.
     */
    private fun fillGaps(group: List<PlacedGlyph>, deskewed: DeskewedMask, vertical: Boolean): List<PlacedGlyph> {
        if (group.size < 3) return group
        val w = group.map { it.width }.sorted()[group.size / 2]
        val h = group.map { it.height }.sorted()[group.size / 2]
        val found = group.toMutableList()
        run {
            // passo da grade nesse eixo: mediana da distância ao vizinho alinhado mais próximo
            fun along(g: PlacedGlyph) = if (vertical) g.y else g.x
            fun across(g: PlacedGlyph) = if (vertical) g.x else g.y
            val tolerance = (if (vertical) w else h) * 0.35f
            val steps = group.mapNotNull { a ->
                group.filter { b -> b !== a && kotlin.math.abs(across(a) - across(b)) < tolerance && along(b) > along(a) }
                    .minOfOrNull { along(it) - along(a) }
            }.sorted()
            if (steps.size < 2) return group
            val pitch = steps[steps.size / 2]
            if (pitch < (if (vertical) h else w) * 0.9f) return group
            for (a in group) {
                val b = group.filter { b -> b !== a && kotlin.math.abs(across(a) - across(b)) < tolerance && along(b) > along(a) }
                    .minByOrNull { along(it) - along(a) } ?: continue
                val slots = ((along(b) - along(a)) / pitch).let { r -> kotlin.math.round(r).toInt().takeIf { kotlin.math.abs(r - it) < 0.3f } } ?: continue
                if (slots !in 2..3) continue
                for (k in 1 until slots) {
                    val t = k / slots.toFloat()
                    val cx = a.x + (b.x - a.x) * t
                    val cy = a.y + (b.y - a.y) * t
                    if (found.any { kotlin.math.abs(it.x - cx) < w / 2 && kotlin.math.abs(it.y - cy) < h / 2 }) continue
                    sampleSlot(deskewed, cx, cy, w, h)?.let { found += it }
                }
            }
        }
        return found
    }

    /** Melhor leitura de um glifo em volta de ([cx], [cy]), ou `null` se ali não há um glifo. */
    private fun sampleSlot(deskewed: DeskewedMask, cx: Float, cy: Float, w: Float, h: Float): PlacedGlyph? {
        var best: PlacedGlyph? = null
        var bestCrisp = GlyphSampler.MIN_CRISPNESS
        for (dy in -2..2) for (dx in -2..2) {
            val x = cx + dx * w * SLOT_SHIFT
            val y = cy + dy * h * SLOT_SHIFT
            val box = PixelBox(
                (x - w / 2).toInt(), (y - h / 2).toInt(), (x + w / 2).toInt(), (y + h / 2).toInt(),
            )
            if (box.left < 0 || box.top < 0 || box.right > deskewed.width || box.bottom > deskewed.height || box.width < 5 || box.height < 5) continue
            val sampled = GlyphSampler.sampleWithQuality(deskewed.mask, deskewed.width, box)
            if (sampled.crispness <= bestCrisp) continue
            val glyph = PlacedGlyph(box.centerX, box.centerY, box.width.toFloat(), box.height.toFloat(), sampled.bits)
            if (glyph.isSolid() || glyph.inkCells() < 5 || !classifier.classify(glyph.bits).isGlyph) continue
            best = glyph
            bestCrisp = sampled.crispness
        }
        return best
    }

    /** Lê os grupos e devolve a leitura (posições na imagem original) com a nota usada na escolha. */
    private fun readGroups(
        image: GrayImage,
        found: List<List<PlacedGlyph>>,
        deskewed: DeskewedMask,
        inkIsDark: Boolean,
    ): Triple<ImageReading, Float, List<List<PlacedGlyph>>> {
        val angle = deskewed.angleDegrees
        if (found.isEmpty()) return ImageReading(ZuReading.EMPTY, emptyList(), inkIsDark, image.width, image.height, mode = mode).let { Triple(it, 0f, found) }

        // A orientação vale para a imagem inteira: soma a pontuação de todos os blocos.
        val (rotation, firstBlocks) = (0..3)
            .map { q -> q to found.map { reader.read(it, rotation = q) } }
            .maxBy { (q, readings) -> readings.sumOf { it.score } - if (q == 0) 0 else ZuReader.ROTATION_COST }
        // Com a orientação escolhida, as colunas de leitura são conhecidas: completa as vagas
        // ao longo delas e relê (só se achou algum glifo novo).
        val columnsVertical = (firstBlocks.first().direction == ReadingDirection.VERTICAL_RIGHT_TO_LEFT) == (rotation % 2 == 0)
        // (a leitura combinada não tem máscara: seus glifos já vêm completados de cada máscara)
        val filled = if (deskewed.mask.isEmpty()) found else found.map { fillGaps(it, deskewed, columnsVertical) }
        val groups = if (filled.sumOf { it.size } > found.sumOf { it.size }) filled else found
        val blocks = if (groups === filled) groups.map { reader.read(it, rotation = rotation) } else firstBlocks
        // Blocos extras quase sem confiança são cenário, não texto (o melhor bloco sempre fica).
        val best = blocks.maxOfOrNull { b -> b.glyphs.sumOf { it.confidence.toDouble() } }
        val ordered = blocks.zip(groups)
            .filter { (reading, _) ->
                reading.glyphs.sumOf { it.confidence.toDouble() } == best ||
                    reading.glyphs.map { it.confidence }.average() >= MIN_EXTRA_BLOCK_CONFIDENCE
            }
            .sortedBy { (_, glyphs) -> ZuLayout.rotate(glyphs, rotation).minOf { it.y - it.height / 2 } }
            .map { (reading, _) -> reading.toSource(deskewed) }

        val merged = ZuReading(
            text = ordered.joinToString("\n") { it.text },
            glyphs = ordered.flatMap { it.glyphs },
            rotation = rotation,
            direction = ordered.firstOrNull()?.direction ?: ZuReading.EMPTY.direction,
            score = ordered.sumOf { it.score },
        )
        // Glifos confiantes somam, duvidosos subtraem: uma máscara que "acha" lixo não vence.
        val quality = merged.glyphs.sumOf { it.confidence.toDouble() - QUALITY_OFFSET }.toFloat()
        return ImageReading(merged, ordered, inkIsDark, image.width, image.height, skewDegrees = -angle, mode = mode).let { Triple(it, quality, groups) }
    }

    /**
     * Junta grupos de tamanho parecido (até [MERGE_SIZE_RATIO]×) que se encostam: numa foto
     * tirada de lado, os glifos de cima e de baixo do mesmo texto ficam com tamanhos diferentes.
     */
    private fun mergeNearbyGroups(groups: List<List<PlacedGlyph>>): List<List<PlacedGlyph>> {
        val merged = groups.map { it.toMutableList() }.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            loop@ for (i in merged.indices) for (j in i + 1 until merged.size) {
                val a = merged[i]
                val b = merged[j]
                val sa = a.map { it.size }.sorted()[a.size / 2]
                val sb = b.map { it.size }.sorted()[b.size / 2]
                if (maxOf(sa, sb) / minOf(sa, sb) > MERGE_SIZE_RATIO) continue
                val pad = maxOf(sa, sb) * MERGE_GAP / 2
                if (bounds(a, pad).intersects(bounds(b, pad))) {
                    a += b
                    merged.removeAt(j)
                    changed = true
                    break@loop
                }
            }
        }
        return merged
    }

    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun intersects(o: Box) = left <= o.right && o.left <= right && top <= o.bottom && o.top <= bottom
    }

    private fun bounds(glyphs: List<PlacedGlyph>, pad: Float) = Box(
        glyphs.minOf { it.x - it.width / 2 } - pad,
        glyphs.minOf { it.y - it.height / 2 } - pad,
        glyphs.maxOf { it.x + it.width / 2 } + pad,
        glyphs.maxOf { it.y + it.height / 2 } + pad,
    )

    companion object {
        /**
         * Leitor para o [mode]: escrita (alfabeto + modelo do inglês) ou números (glifos 0–10, sem modelo).
         * [learned] são os exemplos ensinados pelo usuário; só os do mesmo modo são usados.
         */
        fun forMode(
            mode: ReadingMode,
            direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
            learned: List<ZuGlyph> = emptyList(),
        ): ZuImageReader {
            val examples = learned.filter { g -> g.letters.all { ReadingMode.of(it) == mode } }
            val classifier = GlyphClassifier(alphabet = mode.glyphSet.glyphs, learned = examples)
            return ZuImageReader(ZuReader(classifier, direction, languageModel = mode.usesLanguageModel), classifier, mode)
        }

        /**
         * Leitor para a escolha do usuário: um modo só, ou [ModeChoice.AUTO] (escrita e números
         * lidos em paralelo, fica a leitura de nota maior).
         */
        fun forChoice(
            choice: ModeChoice,
            direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
            learned: List<ZuGlyph> = emptyList(),
        ): ImageTextReader = choice.mode?.let { forMode(it, direction, learned) }
            ?: AutoModeReader(ReadingMode.entries.map { forMode(it, direction, learned) })

        /** Confiança média mínima para manter um bloco que não é o principal. */
        const val MIN_EXTRA_BLOCK_CONFIDENCE = 0.5

        /** Cada glifo vale (confiança − isto) na escolha da melhor máscara. */
        const val QUALITY_OFFSET = 0.4

        /** Leitura fraca (abaixo disto) faz o leitor testar também as versões de cor. */
        const val WEAK_GLYPHS = 4
        const val WEAK_CONFIDENCE = 0.85

        /** Segunda etapa: raios de erosão e janelas das máscaras afinadas. */
        val ERODE_RADII = listOf(1, 2)
        val ERODE_WINDOWS = listOf(0.18f, 0.27f, 0.4f)

        /** Na combinação de máscaras: votos mínimos, ou confiança mínima, para um glifo entrar. */
        const val MIN_VOTES = 2

        /** Passo do ajuste de posição ao ler uma vaga da grade (fração do glifo). */
        const val SLOT_SHIFT = 0.06f
        const val SURE_CONFIDENCE = 0.8f

        /** Grupos com tamanhos até esta proporção, e a até [MERGE_GAP] glifos um do outro, são o mesmo texto. */
        const val MERGE_SIZE_RATIO = 1.5f

        /** Vão máximo, em glifos, entre partes do mesmo texto (ex.: duas palavras na mesma coluna). */
        const val MERGE_GAP = 2.5f

        /** Lados da janela do limiar local, como fração do maior lado da imagem. */
        val ADAPTIVE_WINDOWS = listOf(0.06f, 0.12f, 0.18f, 0.27f, 0.4f, 0.6f)

        /** Janelas da leitura rápida (visor ao vivo). */
        val QUICK_WINDOWS = listOf(0.12f, 0.27f)

        /** Raio do desfoque antes do limiar local = janela / este valor (~2 px para uma janela de 4 glifos de 25 px). */
        const val BLUR_PER_WINDOW = 48
    }

    /** Leva as posições dos glifos de volta para a imagem original (antes de endireitar). */
    private fun ZuReading.toSource(deskewed: DeskewedMask): ZuReading = copy(
        glyphs = glyphs.map { g ->
            val (x, y) = deskewed.toSource(g.source.x, g.source.y)
            g.copy(source = g.source.copy(x = x, y = y))
        },
    )
}
