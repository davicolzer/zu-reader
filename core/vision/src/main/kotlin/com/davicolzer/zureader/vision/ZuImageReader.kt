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
    /** QR code achado na mesma imagem, se houver. */
    val qr: QrCode? = null,
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
    /**
     * Fazer a segunda etapa (versões de cor, afinadas, neon) mesmo quando a primeira não achou
     * quase nada. No modo automático, o leitor de números desliga isto: numa imagem de texto ele
     * não acha números, e a segunda etapa só gastaria tempo.
     */
    private val escalateWhenEmpty: Boolean = true,
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
        if (!escalateWhenEmpty && first.maxOf { it.reading.reading.glyphs.size } < 1) return choose(luma, listOf(first))
        // Segunda etapa: máscaras afinadas (separam glifos que a foto juntou) e versões de cor.
        val thinned = ERODE_RADII.map { r -> candidates(luma, deskew = false, windows = ERODE_WINDOWS, erosion = r) }
        // unlit: sem as manchas de luz das folhas (lápide na sombra de uma árvore)
        val variants = listOf(luma.stretched(), image.unlit(), image.red(), image.green(), image.blue(), image.saturation())
        // letreiros de neon: só o contorno do glifo é desenhado; preenchido, vira o glifo
        // (em cinza e na intensidade da cor, que tira as linhas apagadas da parede atrás do neon)
        val chroma = image.chroma()
        val outlines = FILL_GAPS.flatMap { g -> listOf(luma, chroma).map { candidates(it, deskew = false, windows = QUICK_WINDOWS, fill = g) } }
        return choose(luma, listOf(first) + thinned + outlines + variants.map { candidates(it, deskew = false, windows = QUICK_WINDOWS) })
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
    private fun candidates(image: GrayImage, deskew: Boolean, windows: List<Float>, erosion: Int = 0, fill: Int = -1): List<Candidate> {
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
        return masks.map { (raw, dark) ->
            // [fill] ≥ 0: fecha falhas do contorno (engrossando [fill] px) e preenche o miolo
            val mask = if (fill < 0) raw else Binarizer.fillOutlines(Binarizer.dilate(raw, image.width, image.height, fill), image.width, image.height)
            readWithMask(image, mask, dark, deskew, erosion)
        }
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
        val strips = if (mode != ReadingMode.TEXT) splitStrips(components, deskewed) else emptyList()
        return (GlyphSegmenter.segmentGroups(components) + strips)
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
        // uma caixa bem menor que as outras é um pedaço de glifo (ex.: metade cortada por uma
        // mancha de luz): sai, e a vaga dela é relida inteira
        val pieces = group.filter { it.width < w * PIECE_RATIO || it.height < h * PIECE_RATIO }
        val cells = group - pieces.toSet()
        if (cells.size < 3) return group
        val found = cells.toMutableList()
        run {
            // passo da grade nesse eixo: mediana da distância ao vizinho alinhado mais próximo
            fun along(g: PlacedGlyph) = if (vertical) g.y else g.x
            fun across(g: PlacedGlyph) = if (vertical) g.x else g.y
            val tolerance = (if (vertical) w else h) * 0.35f
            val steps = cells.mapNotNull { a ->
                cells.filter { b -> b !== a && kotlin.math.abs(across(a) - across(b)) < tolerance && along(b) > along(a) }
                    .minOfOrNull { along(it) - along(a) }
            }.sorted()
            if (steps.size < 2) return group
            val pitch = steps[steps.size / 2]
            if (pitch < (if (vertical) h else w) * 0.9f) return group
            for (a in cells) {
                val b = cells.filter { b -> b !== a && kotlin.math.abs(across(a) - across(b)) < tolerance && along(b) > along(a) }
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
            // pontas das colunas: uma vaga logo antes do primeiro ou depois do último glifo, só se
            // uma coluna vizinha tem glifo nessa mesma altura (a grade continua ali) e o desenho
            // bate quase exato com uma letra
            for (a in cells) for (sign in listOf(-1f, 1f)) {
                val target = along(a) + sign * pitch
                val cx = if (vertical) a.x else target
                val cy = if (vertical) target else a.y
                if (found.any { kotlin.math.abs(it.x - cx) < w / 2 && kotlin.math.abs(it.y - cy) < h / 2 }) continue
                val neighborRow = cells.any { g -> g !== a && kotlin.math.abs(along(g) - target) < pitch * 0.3f && kotlin.math.abs(across(g) - across(a)) < pitch * 1.6f }
                if (!neighborRow) continue
                sampleSlot(deskewed, cx, cy, w, h, maxDistance = END_MAX_DISTANCE)?.let { found += it }
            }
        }
        return found
    }

    /**
     * Placas de números: os números ficam encostados e as molduras se juntam numa faixa só, com
     * vizinhos dividindo a linha de borda (cada número ocupa 4 blocos na faixa, mais 1 da borda
     * final). Uma mancha comprida com a largura de um glifo é fatiada assim; cada fatia de 5×5
     * blocos que for um número vira um glifo. Cada faixa vira um grupo.
     */
    private fun splitStrips(components: List<PixelBox>, deskewed: DeskewedMask): List<List<PixelBox>> = splitStripsOnly(components, deskewed) + equalsSigns(components)

    /**
     * Sinal de igual nas placas de contas ("1 2 7 = 10"): dois traços da largura de um número,
     * um em cima do outro (ou lado a lado, na foto girada). Vira uma caixa quadrada no meio dos
     * dois, que a amostragem lê como o desenho de [ZuNumerals] para `=`.
     */
    private fun equalsSigns(components: List<PixelBox>): List<List<PixelBox>> {
        val bars = components.filter { c ->
            val long = maxOf(c.width, c.height)
            val short = minOf(c.width, c.height)
            long >= GlyphSegmenter.MIN_GLYPH_PX && long >= short * 3.5f && c.area >= 0.6f * c.width * c.height
        }
        val used = HashSet<PixelBox>()
        val signs = mutableListOf<PixelBox>()
        for (a in bars) for (b in bars) {
            if (a === b || a in used || b in used) continue
            val horizontal = a.width > a.height
            if (horizontal != (b.width > b.height)) continue
            val long = maxOf(a.width, a.height).toFloat()
            val short = minOf(a.width, a.height).toFloat()
            if (kotlin.math.abs(maxOf(b.width, b.height) - long) > long * 0.15f) continue
            // alinhados e separados por mais ou menos um traço
            val aligned = if (horizontal) kotlin.math.abs(a.left - b.left) < long * 0.2f else kotlin.math.abs(a.top - b.top) < long * 0.2f
            val gap = if (horizontal) b.top - a.bottom else b.left - a.right
            if (!aligned || gap < short * 0.3f || gap > short * 2.5f) continue
            used += a; used += b
            // caixa: os dois traços com um traço de folga de cada lado (vazio, traço, vão, traço, vazio)
            val left = minOf(a.left, b.left); val right = maxOf(a.right, b.right)
            val top = minOf(a.top, b.top); val bottom = maxOf(a.bottom, b.bottom)
            signs += if (horizontal) {
                PixelBox(left, (top - short).toInt(), right, (bottom + short).toInt())
            } else {
                PixelBox((left - short).toInt(), top, (right + short).toInt(), bottom)
            }
        }
        return signs.map { listOf(it) }
    }

    private fun splitStripsOnly(components: List<PixelBox>, deskewed: DeskewedMask): List<List<PixelBox>> =
        components.mapNotNull { c ->
            val vertical = c.height >= c.width
            val across = if (vertical) c.width else c.height
            val along = if (vertical) c.height else c.width
            if (across < GlyphSegmenter.MIN_GLYPH_PX || along < across * 1.6f) return@mapNotNull null
            // quantos números cabem: o n em que o bloco (altura ÷ (4n+1)) deixa a largura mais perto
            // de 5 blocos (as bordas entre números podem sair mais finas na foto)
            val n = (2..MAX_STRIP).minByOrNull { k -> kotlin.math.abs(across / (along / (4 * k + 1f)) - 5f) } ?: return@mapNotNull null
            val block = along / (4 * n + 1f)
            if (kotlin.math.abs(across / block - 5f) > STRIP_TOLERANCE) return@mapNotNull null
            val slices = (0 until n).map { k ->
                val start = (if (vertical) c.top else c.left) + k * 4 * block
                if (vertical) PixelBox(c.left, start.toInt(), c.right, (start + 5 * block).toInt().coerceAtMost(deskewed.height))
                else PixelBox(start.toInt(), c.top, (start + 5 * block).toInt().coerceAtMost(deskewed.width), c.bottom)
            }.filter { box ->
                val sampled = GlyphSampler.sampleWithQuality(deskewed.mask, deskewed.width, box)
                sampled.crispness >= GlyphSampler.MIN_CRISPNESS && classifier.classify(sampled.bits).isGlyph
            }
            slices.takeIf { it.size >= 2 }
        }

    /**
     * Tira caixas bem menores que as outras do bloco: são pedaços de glifo (ex.: a metade de cima,
     * separada da de baixo por uma mancha de luz), que ocupariam a vaga do glifo inteiro.
     */
    private fun withoutPieces(group: List<PlacedGlyph>): List<PlacedGlyph> {
        if (group.size < 3) return group
        val w = group.map { it.width }.sorted()[group.size / 2]
        val h = group.map { it.height }.sorted()[group.size / 2]
        // e bem maiores também: vários glifos colados numa caixa só (que esconderia os de baixo)
        return group.filterNot {
            it.width < w * PIECE_RATIO || it.height < h * PIECE_RATIO || it.width > w * MERGED_RATIO || it.height > h * MERGED_RATIO
        }
    }

    /** Melhor leitura de um glifo em volta de ([cx], [cy]), ou `null` se ali não há um glifo. */
    private fun sampleSlot(deskewed: DeskewedMask, cx: Float, cy: Float, w: Float, h: Float, maxDistance: Int = Int.MAX_VALUE): PlacedGlyph? {
        // entre as posições testadas, fica com a que bate melhor com um glifo (desempate: nitidez)
        var best: PlacedGlyph? = null
        var bestDistance = Int.MAX_VALUE
        var bestCrisp = 0f
        for (dy in -2..2) for (dx in -2..2) {
            val x = cx + dx * w * SLOT_SHIFT
            val y = cy + dy * h * SLOT_SHIFT
            val box = PixelBox(
                (x - w / 2).toInt(), (y - h / 2).toInt(), (x + w / 2).toInt(), (y + h / 2).toInt(),
            )
            if (box.left < 0 || box.top < 0 || box.right > deskewed.width || box.bottom > deskewed.height || box.width < 5 || box.height < 5) continue
            val sampled = GlyphSampler.sampleWithQuality(deskewed.mask, deskewed.width, box)
            if (sampled.crispness < GlyphSampler.MIN_CRISPNESS) continue
            val glyph = PlacedGlyph(box.centerX, box.centerY, box.width.toFloat(), box.height.toFloat(), sampled.bits)
            if (glyph.isSolid() || glyph.inkCells() < 5) continue
            val match = classifier.classify(glyph.bits).best ?: continue
            if (match.distance > minOf(maxDistance, GlyphClassifier.MAX_DISTANCE)) continue
            if (match.distance > bestDistance || (match.distance == bestDistance && sampled.crispness <= bestCrisp)) continue
            best = glyph
            bestDistance = match.distance
            bestCrisp = sampled.crispness
        }
        return best
    }

    /** Lê os grupos e devolve a leitura (posições na imagem original) com a nota usada na escolha. */
    private fun readGroups(
        image: GrayImage,
        raw: List<List<PlacedGlyph>>,
        deskewed: DeskewedMask,
        inkIsDark: Boolean,
    ): Triple<ImageReading, Float, List<List<PlacedGlyph>>> {
        val angle = deskewed.angleDegrees
        val found = raw.map(::withoutPieces).filter { it.isNotEmpty() }
        if (found.isEmpty()) return ImageReading(ZuReading.EMPTY, emptyList(), inkIsDark, image.width, image.height, mode = mode).let { Triple(it, 0f, found) }

        // A foto já vem em pé (o app confia na orientação dela): não tenta outros giros.
        val rotation = 0
        val firstBlocks = found.map { reader.read(it) }
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
            escalateWhenEmpty: Boolean = true,
        ): ZuImageReader {
            val examples = if (mode == ReadingMode.MIXED) learned else learned.filter { g -> g.letters.all { ReadingMode.of(it) == mode } }
            val classifier = GlyphClassifier(alphabet = mode.glyphSet.glyphs, learned = examples)
            return ZuImageReader(ZuReader(classifier, direction, languageModel = mode.usesLanguageModel), classifier, mode, escalateWhenEmpty)
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
            ?: AutoModeReader(ReadingMode.entries.map { forMode(it, direction, learned, escalateWhenEmpty = it != ReadingMode.NUMBERS) })

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

        /** Neon: quanto engrossar o contorno (px) antes de preencher o miolo, para fechar falhas. */
        val FILL_GAPS = listOf(1)

        /** Passo do ajuste de posição ao ler uma vaga da grade (fração do glifo). */
        const val SLOT_SHIFT = 0.06f

        /** Faixa de números: até tantos números encostados. */
        const val MAX_STRIP = 8

        /** Faixa de números: quanto a largura pode fugir de 5 blocos. */
        const val STRIP_TOLERANCE = 0.8f

        /** Na ponta de uma coluna, o glifo achado precisa bater com uma letra a até tantos blocos. */
        const val END_MAX_DISTANCE = 3

        /** Caixa com largura ou altura abaixo disto × a do bloco é pedaço de glifo. */
        const val PIECE_RATIO = 0.65f

        /** Caixa com largura ou altura acima disto × a do bloco é vários glifos colados. */
        const val MERGED_RATIO = 1.6f
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
