package com.davicolzer.zureader.vision

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** As 7 peças de 4 blocos (como as do Tetris). */
enum class Tetromino(internal val base: List<Cell>) {
    I(listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3))),
    O(listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(1, 1))),
    T(listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1))),
    S(listOf(Cell(0, 1), Cell(0, 2), Cell(1, 0), Cell(1, 1))),
    Z(listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(1, 2))),
    L(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1))),
    J(listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(2, 0))),
}

/** Uma célula de uma peça: linha e coluna (a linha cresce para baixo). */
data class Cell(val row: Int, val col: Int)

/**
 * Uma peça de uma sequência de comandos: o formato e quantos quartos de volta (no sentido horário)
 * ela está girada em relação ao desenho base de [Tetromino]. Formato nulo = não reconhecida.
 * Peças simétricas têm menos rotações: O só 0; I, S e Z só 0 e 1.
 */
data class CommandPiece(val shape: Tetromino?, val rotation: Int = 0) {
    /** As células como a peça aparece numa coluna (em pé: ¼ de volta no sentido horário). */
    val cellsInColumn: List<Cell> get() = cells.takeIf { it.isNotEmpty() }?.let(::rotateClockwise) ?: emptyList()

    /** Código curto, como "T2" (ou "?"); a sequência inteira é guardada assim. */
    val code: String get() = shape?.let { "${it.name}$rotation" } ?: "?"

    /** As 4 células do desenho, encostadas em cima e à esquerda (vazio se não reconhecida). */
    val cells: List<Cell>
        get() = shape?.let { s -> (0 until rotation).fold(normalize(s.base)) { c, _ -> rotateClockwise(c) } } ?: emptyList()

    companion object {
        fun parse(code: String): CommandPiece {
            val shape = Tetromino.entries.firstOrNull { it.name == code.take(1) } ?: return CommandPiece(null)
            return CommandPiece(shape, code.drop(1).toIntOrNull() ?: 0)
        }
    }
}

/** Os botões do jogo que as peças representam. [label] = o que mostrar e copiar. */
enum class GameButton(val label: String) {
    UP("↑"), DOWN("↓"), LEFT("←"), RIGHT("→"), JUMP("A"), ROTATE_LEFT("LT"), ROTATE_RIGHT("RT");

    companion object {
        /**
         * O botão de cada peça (na orientação de leitura, com a coluna já deitada):
         * - quadrado (O) = pular (A);
         * - T = seta: a perna aponta a direção (┴ cima, ┬ baixo, ┤ esquerda, ├ direita);
         * - S e Z = girar o mundo: S = RT e Z = LT (conferido no jogo pelo usuário).
         * I, L e J (e peça não reconhecida) não têm botão: `null`.
         */
        fun of(piece: CommandPiece): GameButton? = when (piece.shape) {
            Tetromino.O -> JUMP
            Tetromino.T -> when (piece.rotation) {
                0 -> DOWN
                1 -> LEFT
                2 -> UP
                else -> RIGHT
            }
            Tetromino.S -> ROTATE_RIGHT
            Tetromino.Z -> ROTATE_LEFT
            else -> null
        }
    }
}

/** Onde uma peça está na imagem lida, em frações da largura e da altura (0 a 1). */
data class PieceBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * A sequência lida, na ordem de leitura (uma coluna já vem girada como se fosse uma linha).
 * [boxes]: onde cada peça está na imagem, na mesma ordem (vazio se não se sabe).
 */
data class CommandReading(val pieces: List<CommandPiece>, val boxes: List<PieceBox> = emptyList()) {
    val code: String get() = pieces.joinToString(" ") { it.code }
    val recognized: Int get() = pieces.count { it.shape != null }

    /** `true` se as peças estão uma embaixo da outra na imagem (pelas posições; sem elas, `false`). */
    val vertical: Boolean
        get() = boxes.size >= 2 && (boxes.maxOf { it.top } - boxes.minOf { it.top }) > (boxes.maxOf { it.left } - boxes.minOf { it.left })

    /** As posições em texto ("l,t,r,b;…"), para guardar junto com o código. */
    val boxesCode: String
        get() = boxes.joinToString(";") { b -> listOf(b.left, b.top, b.right, b.bottom).joinToString(",") { "%.4f".format(java.util.Locale.ROOT, it) } }

    companion object {
        fun parse(code: String, boxesCode: String = ""): CommandReading {
            val pieces = code.split(' ').filter { it.isNotBlank() }.map(CommandPiece::parse)
            val boxes = boxesCode.split(';').mapNotNull { part ->
                part.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }?.let { PieceBox(it[0], it[1], it[2], it[3]) }
            }
            return CommandReading(pieces, if (boxes.size == pieces.size) boxes else emptyList())
        }
    }
}

/** Gira as células 90° no sentido horário (na tela) e encosta em cima e à esquerda. */
internal fun rotateClockwise(cells: List<Cell>): List<Cell> = normalize(cells.map { Cell(it.col, -it.row) })

internal fun normalize(cells: Collection<Cell>): List<Cell> {
    val r0 = cells.minOf { it.row }
    val c0 = cells.minOf { it.col }
    return cells.map { Cell(it.row - r0, it.col - c0) }.sortedWith(compareBy({ it.row }, { it.col }))
}

/**
 * Lê as sequências de comandos do jogo: peças de 4 blocos enfileiradas numa linha ou numa coluna
 * (em paredes, pisos, pilares, murais). A imagem já deve estar recortada (e endireitada) em volta
 * da sequência.
 *
 * Como funciona: separa a tinta do fundo (testa limiar global e local, peça clara ou escura) e
 * junta as partes de cada peça (sombras e bordas a separam); cada mancha vira uma grade de blocos
 * pelo tamanho da própria mancha (4 blocos); fica a combinação que dá mais peças reconhecidas.
 * Peças coladas numa linha só (o jeito mais comum no jogo) são lidas como uma mancha comprida:
 * endireitada, coberta pela grade de blocos mais nítida e dividida em peças de 4, na ordem de leitura.
 * Numa coluna (lida de cima para baixo), cada peça é girada um quarto de volta no sentido
 * anti-horário, como se a coluna fosse deitada: a mesma sequência dá o mesmo código em linha ou coluna.
 */
object CommandReader {
    /** Lado maior da imagem analisada (a foto é reduzida antes). */
    private const val WORK_SIDE = 900
    private val CLOSINGS = floatArrayOf(0f, 0.15f, 0.3f, 0.5f, 0.8f)

    private val byCells: Map<List<Cell>, CommandPiece> = buildMap {
        for (shape in Tetromino.entries) {
            var cells = normalize(shape.base)
            for (q in 0..3) {
                putIfAbsent(cells, CommandPiece(shape, q))
                cells = rotateClockwise(cells)
            }
        }
    }

    /** Todas as posições distintas das peças (19), para dividir peças coladas. */
    private val placements: List<List<Cell>> = byCells.keys.toList()

    fun read(image: RgbImage): CommandReading {
        val gray = downscale(image.luma())
        val masks = inkMasks(gray)
        // peças coladas numa linha só: uma mancha comprida, que cobre o recorte e se divide em peças
        // de 4; quando existe, vale ela (lida como separadas, a linha colada vira pedaços soltos)
        // entre as máscaras, a leitura com menos peças fora dos códigos e de blocos maiores (menos
        // células): uma máscara que pega só pedaços da tinta dá grades finas e nítidas que não são a sequência
        val chain = masks.mapNotNull { readChain(it, gray.width, gray.height) }.minByOrNull { it.first }?.second
        // a leitura colada vale se só tem formatos dos códigos (ou se a separada não acha nada):
        // peças separadas numa foto em perspectiva, lidas como coladas, viram I, L e J
        if (chain != null && odd(chain) == 0) return chain
        val separate = readSeparate(masks, gray.width, gray.height)
        if (chain == null) return separate
        // com peças estranhas, a colada só perde para uma leitura separada toda reconhecida e com
        // pelo menos tantas peças
        val separateClean = separate.pieces.isNotEmpty() && separate.recognized == separate.pieces.size
        return if (separateClean && separate.pieces.size >= chain.pieces.size) separate else chain
    }

    /** Peças separadas (cada uma é uma mancha). */
    private fun readSeparate(masks: List<BooleanArray>, width: Int, height: Int): CommandReading {
        var best: Candidate? = null
        for (mask in masks) {
            for (close in CLOSINGS) {
                val found = blobs(mask, width, height, close)
                val candidate = Candidate(found, mask, close)
                if (best == null || candidate.score > best.score) best = candidate
            }
        }
        var found = best?.blobs ?: return CommandReading(emptyList())
        // depois de escolhida a leitura: peças menores na mesma fila (as do fundo de uma foto em
        // perspectiva), reconhecidas e com ao menos 30% do tamanho típico das outras
        if (best.mask != null && found.count { it.piece.shape != null } >= 2) found = withSmallPieces(found, best.mask, width, height, best.close)
        // manchas não reconhecidas bem menores que as peças são sujeira (um símbolo ao lado, um risco)
        val pieceArea = found.filter { it.piece.shape != null && it.area > 0 }.map { it.area }.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        val blobs = found.filter { it.piece.shape != null || it.area == 0 || it.area >= 0.6f * pieceArea }
        if (blobs.isEmpty()) return CommandReading(emptyList())
        val xs = blobs.map { it.cx }
        val ys = blobs.map { it.cy }
        val horizontal = (xs.max() - xs.min()) >= (ys.max() - ys.min())
        val ordered = blobs.sortedBy { if (horizontal) it.cx else it.cy }
        val boxes = ordered.map { b ->
            val box = b.box ?: intArrayOf(b.cx.toInt(), b.cy.toInt(), b.cx.toInt() + 1, b.cy.toInt() + 1)
            PieceBox(box[0].toFloat() / width, box[1].toFloat() / height, box[2].toFloat() / width, box[3].toFloat() / height)
        }
        return CommandReading(ordered.map { if (horizontal) it.piece else turnCounterClockwise(it.piece) }, boxes)
    }

    /**
     * Peças coladas numa linha só (o caso comum nas sequências do jogo): a maior mancha cobre quase
     * todo o comprimento do recorte. Ela é posta em pé (uma linha é girada ¼ de volta no sentido
     * horário), endireitada (cada fileira de pixels é deslocada pela inclinação do eixo) e coberta
     * por grades de 2 a 5 blocos de largura; fica a grade com as células mais nítidas (bem cheias
     * ou bem vazias) que se divide em peças de 4. Devolve o número de células e a leitura.
     */
    private fun readChain(ink: BooleanArray, w: Int, h: Int): Pair<Int, CommandReading>? {
        var mask = fillHoles(dilate(erode(ink, w, h, 1), w, h, 1), w, h)
        val first = sequenceBlobs(mask, w, h)?.second ?: return null
        // moldura: fileiras (ou colunas) perto das pontas que atravessam o recorte inteiro
        val vertical = first[3] - first[1] >= first[2] - first[0]
        mask = mask.copyOf()
        if (vertical) {
            for (y in 0 until h) {
                if (y > 0.15f * h && y < 0.85f * h) continue
                if ((0 until w).count { mask[y * w + it] } >= 0.9f * w) for (x in 0 until w) mask[y * w + x] = false
            }
        } else {
            for (x in 0 until w) {
                if (x > 0.15f * w && x < 0.85f * w) continue
                if ((0 until h).count { mask[it * w + x] } >= 0.9f * h) for (y in 0 until h) mask[y * w + x] = false
            }
        }
        mask = dilate(erode(mask, w, h, 1), w, h, 1)
        val (keep, box) = sequenceBlobs(mask, w, h) ?: return null
        val bw = box[2] - box[0]
        val bh = box[3] - box[1]
        if (maxOf(bw, bh) < 0.7f * maxOf(w, h) || minOf(bw, bh) < 4) return null
        // em pé: linha → girada ¼ de volta no sentido horário (o começo fica em cima)
        val horizontal = bw > bh
        val sh = if (horizontal) bw else bh
        val sw = if (horizontal) bh else bw
        val sub = BooleanArray(sw * sh) { k ->
            val r = k / sw
            val c = k % sw
            val (x, y) = if (horizontal) (box[0] + r) to (box[1] + bh - 1 - c) else (box[0] + c) to (box[1] + r)
            keep[y * w + x]
        }
        // a coluna como está e endireitada (a inclinação medida pelos centros das fileiras pode
        // ser falsa numa coluna reta com dentes de um lado só): fica a grade mais nítida
        val options = listOf(Straight(sub, sw, IntArray(sh)), straighten(sub, sw, sh)).flatMap { readGrid(it, sh) }
        if (options.isEmpty()) return null
        // a com menos peças que não aparecem nos códigos (I, L, J: sinal de grade errada) e, depois,
        // a de blocos maiores (menos células)
        val best = options.minWith(compareBy({ odd(it.reading) }, { it.cells }))
        // as caixas das peças: da mancha em pé de volta para a imagem
        val boxes = best.rects.map { r ->
            val (x0, y0, x1, y1) = if (horizontal) {
                listOf(box[0] + r[1], box[1] + bh - r[2], box[0] + r[3], box[1] + bh - r[0])
            } else {
                listOf(box[0] + r[0], box[1] + r[1], box[0] + r[2], box[1] + r[3])
            }
            PieceBox(x0 / w, y0 / h, x1 / w, y1 / h)
        }
        return odd(best.reading) * 10_000 + best.cells to best.reading.copy(boxes = boxes)
    }

    /**
     * As manchas da sequência: as não minúsculas (≥ 0,2% da imagem) cujo centro cai na faixa da
     * maior mancha, no eixo transversal (um símbolo ou um pedaço de moldura ao lado fica de fora).
     * Devolve os pixels delas e a caixa [x0, y0, x1, y1) que as junta; nulo se não há nenhuma.
     */
    private fun sequenceBlobs(mask: BooleanArray, w: Int, h: Int): Pair<BooleanArray, IntArray>? {
        val labels = label(mask, w, h)
        val big = labels.areas.indices.filter { labels.areas[it] > 0.002f * w * h }
        if (big.isEmpty()) return null
        val all = intArrayOf(big.minOf { labels.boxes[it][0] }, big.minOf { labels.boxes[it][1] }, big.maxOf { labels.boxes[it][2] }, big.maxOf { labels.boxes[it][3] })
        val vertical = all[3] - all[1] >= all[2] - all[0]
        val main = labels.boxes[big.maxBy { labels.areas[it] }]
        val (lo, hi) = if (vertical) main[0] to main[2] else main[1] to main[3]
        val margin = 0f
        val kept = big.filter { i ->
            val b = labels.boxes[i]
            val center = if (vertical) (b[0] + b[2]) / 2f else (b[1] + b[3]) / 2f
            center >= lo - margin && center <= hi + margin
        }.toSet()
        // as manchas juntadas têm que ser quase toda a tinta (senão não é uma sequência colada)
        if (kept.sumOf { labels.areas[it] } < 0.85f * big.sumOf { labels.areas[it] }) return null
        val pixels = BooleanArray(w * h) { labels.ids[it] > 0 && (labels.ids[it] - 1) in kept }
        val box = intArrayOf(kept.minOf { labels.boxes[it][0] }, kept.minOf { labels.boxes[it][1] }, kept.maxOf { labels.boxes[it][2] }, kept.maxOf { labels.boxes[it][3] })
        return pixels to box
    }

    /** A melhor grade de blocos sobre a mancha em pé [straight] (largura [sw2], altura [sh]), dividida em peças. */
    /** Uma leitura de grade: as peças e, para cada uma, o retângulo [x0, y0, x1, y1] na mancha em pé (sem endireitar). */
    private class GridReading(val crisp: Float, val cells: Int, val reading: CommandReading, val rects: List<FloatArray>)

    private class Grid(val crisp: Float, val cells: List<Cell>, val bx: Float, val by: Float, val ox: Float, val oy: Float)

    private fun readGrid(st: Straight, sh: Int): List<GridReading> {
        val straight = st.mask
        val sw2 = st.width
        // nitidez de cada grade: a média, por célula, do quanto ela está longe de cheia ou vazia
        val integral = IntArray((sw2 + 1) * (sh + 1))
        for (y in 0 until sh) {
            var row = 0
            for (x in 0 until sw2) {
                if (straight[y * sw2 + x]) row++
                integral[(y + 1) * (sw2 + 1) + x + 1] = integral[y * (sw2 + 1) + x + 1] + row
            }
        }
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int): Float {
            val area = (x1 - x0) * (y1 - y0)
            if (area <= 0) return 0f
            val count = integral[y1 * (sw2 + 1) + x1] - integral[y0 * (sw2 + 1) + x1] - integral[y1 * (sw2 + 1) + x0] + integral[y0 * (sw2 + 1) + x0]
            return count.toFloat() / area
        }
        // blocos de 1/6 a 2/3 da largura, quase quadrados, e a grade deslocada em sextos de bloco
        // (a borda da mancha nem sempre cai na divisa de um bloco)
        val grids = mutableListOf<Grid>()
        var sharpestAny = Float.MAX_VALUE
        val step = maxOf(0.5f, sw2 / 160f)
        var bx = sw2 / 6f
        while (bx <= sw2 / 1.5f) {
            for (ky in floatArrayOf(0.85f, 1f, 1.15f)) {
                val by = bx * ky
                for (i in 0 until 6) for (j in 0 until 6) {
                    val ox = bx * i / 6
                    val oy = by * j / 6
                    var blur = 0f
                    var count = 0
                    val cells = mutableListOf<Cell>()
                    var r = 0
                    var y = -oy
                    while (y < sh) {
                        val y0 = y.toInt().coerceAtLeast(0)
                        val y1 = (y + by).toInt().coerceAtMost(sh)
                        var c = 0
                        var x = -ox
                        while (x < sw2) {
                            val x0 = x.toInt().coerceAtLeast(0)
                            val x1 = (x + bx).toInt().coerceAtMost(sw2)
                            // pedaços de célula na borda (menos de 30%) não contam
                            if ((x1 - x0) * (y1 - y0) >= 0.3f * bx * by) {
                                val f = fill(x0, y0, x1, y1)
                                blur += minOf(f, 1 - f)
                                count++
                                if (f > 0.5f) cells += Cell(r, c)
                            }
                            c++
                            x += bx
                        }
                        r++
                        y += by
                    }
                    if (count == 0) continue
                    val crisp = blur / count
                    if (crisp < sharpestAny) sharpestAny = crisp
                    if (cells.size >= 8 && cells.size % 4 == 0) grids += Grid(crisp, cells, bx, by, ox, oy)
                }
            }
            bx += step
        }
        // só as grades quase tão nítidas quanto a melhor (uma mais fina, de blocos divididos, é tão
        // nítida quanto a certa), dos blocos maiores para os menores; a primeira que se divide só em
        // formatos dos códigos é a escolhida (dividir é o passo caro)
        if (grids.isEmpty()) return emptyList()
        val sharpest = sharpestAny
        if (sharpest > MAX_CHAIN_BLUR) return emptyList()
        val out = mutableListOf<GridReading>()
        for (g in grids.filter { it.crisp <= minOf(sharpest + 0.03f, MAX_CHAIN_BLUR) }.sortedWith(compareBy({ it.cells.size }, { it.crisp })).take(8)) {
            val pieces = split(g.cells, horizontal = false) ?: continue
            // em pé, como uma coluna: cada peça é deitada de volta
            val reading = CommandReading(pieces.map { turnCounterClockwise(byCells.getValue(normalize(it))) })
            // retângulo de cada peça: as células na grade, desfeito o endireitamento da fileira do meio
            val rects = pieces.map { p ->
                val y0 = (p.minOf { it.row } * g.by - g.oy).coerceIn(0f, sh.toFloat())
                val y1 = ((p.maxOf { it.row } + 1) * g.by - g.oy).coerceIn(0f, sh.toFloat())
                val shift = st.offsets[((y0 + y1) / 2).toInt().coerceIn(0, sh - 1)]
                val x0 = (p.minOf { it.col } * g.bx - g.ox - shift).coerceAtLeast(0f)
                val x1 = ((p.maxOf { it.col } + 1) * g.bx - g.ox - shift).coerceAtLeast(x0 + 1)
                floatArrayOf(x0, y0, x1, y1)
            }
            out += GridReading(g.crisp, g.cells.size, reading, rects)
            if (odd(reading) == 0) break
        }
        return out
    }

    /** Grades mais borradas que isso não são uma sequência de blocos. */
    private const val MAX_CHAIN_BLUR = 0.13f

    private class Straight(val mask: BooleanArray, val width: Int, val offsets: IntArray)

    /**
     * Endireita uma mancha em pé e inclinada: desloca cada fileira pela reta que passa pelos centros.
     * [Straight.offsets]: quanto cada fileira andou (x endireitado = x original + deslocamento).
     */
    private fun straighten(mask: BooleanArray, w: Int, h: Int): Straight {
        var n = 0
        var sy = 0.0
        var sx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (y in 0 until h) {
            var left = -1
            var right = -1
            for (x in 0 until w) if (mask[y * w + x]) { if (left < 0) left = x; right = x }
            if (left < 0) continue
            val cx = (left + right) / 2.0
            n++; sy += y; sx += cx; syy += y.toDouble() * y; sxy += y * cx
        }
        if (n < 2) return Straight(mask, w, IntArray(h))
        val slope = (n * sxy - sy * sx) / (n * syy - sy * sy)
        val shifts = IntArray(h) { (-slope * it).roundToInt() }
        val minShift = shifts.min()
        val maxShift = shifts.max()
        val wide = w + maxShift - minShift
        val out = BooleanArray(wide * h)
        for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x]) out[y * wide + x + shifts[y] - minShift] = true
        // corta as colunas vazias dos lados
        val used = (0 until wide).filter { x -> (0 until h).any { y -> out[y * wide + x] } }
        val x0 = used.first()
        val x1 = used.last() + 1
        val tight = BooleanArray((x1 - x0) * h) { k -> out[(k / (x1 - x0)) * wide + x0 + k % (x1 - x0)] }
        return Straight(tight, x1 - x0, IntArray(h) { shifts[it] - minShift - x0 })
    }

    /** Acrescenta peças pequenas alinhadas com a fila das já achadas (ver [readSeparate]). */
    private fun withSmallPieces(found: List<Blob>, mask: BooleanArray, w: Int, h: Int, close: Float): List<Blob> {
        val pieces = found.filter { it.piece.shape != null && it.area > 0 }
        if (pieces.size < 2) return found
        val typical = pieces.map { it.area }.sorted()[pieces.size / 2]
        val side = sqrt(typical / 4f)
        val xs = pieces.map { it.cx }
        val ys = pieces.map { it.cy }
        val vertical = (ys.max() - ys.min()) > (xs.max() - xs.min())
        // a fila: reta pelos centros das peças (o eixo transversal em função do eixo de leitura)
        val along = pieces.map { if (vertical) it.cy else it.cx }
        val across = pieces.map { if (vertical) it.cx else it.cy }
        val ma = along.average()
        val mc = across.average()
        val varA = along.sumOf { (it - ma) * (it - ma).toDouble() }
        val slope = if (varA > 0) along.indices.sumOf { (along[it] - ma) * (across[it] - mc).toDouble() } / varA else 0.0
        val extra = blobs(mask, w, h, close, minFrac = 0.0004f).filter { b ->
            if (b.piece.shape == null || b.area < 0.3f * typical) return@filter false
            val a = if (vertical) b.cy else b.cx
            val c = if (vertical) b.cx else b.cy
            val expected = mc + slope * (a - ma)
            // na fila e sem repetir uma peça já achada
            abs(c - expected) <= 2 * side && found.none { abs(it.cx - b.cx) < side && abs(it.cy - b.cy) < side }
        }
        // manchas não reconhecidas fora da fila (um desenho ao lado, o chão) saem
        val inLine = { b: Blob ->
            val a = if (vertical) b.cy else b.cx
            val c = if (vertical) b.cx else b.cy
            abs(c - (mc + slope * (a - ma))) <= 2 * side
        }
        // e as bem maiores que uma peça (o chão, um desenho grande)
        return found.filter { it.piece.shape != null || (inLine(it) && it.area <= 3 * typical) } + extra
    }

    /** A peça como fica com a coluna deitada (um quarto de volta no sentido anti-horário). */
    private fun turnCounterClockwise(piece: CommandPiece): CommandPiece {
        if (piece.shape == null) return piece
        val cells = rotateClockwise(rotateClockwise(rotateClockwise(piece.cells)))
        return byCells.getValue(cells)
    }

    private class Blob(val cx: Float, val cy: Float, val piece: CommandPiece, val area: Int = 0, val box: IntArray? = null)

    private class Candidate(val blobs: List<Blob>, val mask: BooleanArray? = null, val close: Float = 0f) {
        private val ok = blobs.count { it.piece.shape != null }
        /** Mais peças reconhecidas e menos manchas que não são peça. */
        val score: Int = (ok - (blobs.size - ok)) * 1000 + ok
    }

    // --- tinta ---

    private fun downscale(gray: GrayImage): GrayImage {
        val side = maxOf(gray.width, gray.height)
        if (side <= WORK_SIDE) return gray
        val f = side.toFloat() / WORK_SIDE
        val w = (gray.width / f).roundToInt().coerceAtLeast(1)
        val h = (gray.height / f).roundToInt().coerceAtLeast(1)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = (x * f).toInt()
            val x1 = ((x + 1) * f).toInt().coerceIn(x0 + 1, gray.width)
            val y0 = (y * f).toInt()
            val y1 = ((y + 1) * f).toInt().coerceIn(y0 + 1, gray.height)
            var sum = 0
            for (yy in y0 until y1) for (xx in x0 until x1) sum += gray[xx, yy]
            out[y * w + x] = sum / ((x1 - x0) * (y1 - y0))
        }
        return GrayImage(w, h, out)
    }

    /** Candidatos de tinta: limiar global e local, peça escura ou clara; descarta os com tinta de menos ou demais. */
    private fun inkMasks(gray: GrayImage): List<BooleanArray> {
        val out = mutableListOf<BooleanArray>()
        val t = Binarizer.otsu(gray)
        out += BooleanArray(gray.pixels.size) { gray.pixels[it] <= t }
        out += BooleanArray(gray.pixels.size) { gray.pixels[it] > t }
        for (frac in floatArrayOf(0.6f, 1.2f)) {
            val radius = (maxOf(gray.width, gray.height) * frac / 8).toInt().coerceAtLeast(7)
            val local = gray.boxBlur(radius)
            for (dark in booleanArrayOf(true, false)) {
                val diff = IntArray(gray.pixels.size) { i ->
                    val d = local.pixels[i] - gray.pixels[i]
                    (if (dark) d else -d).coerceAtLeast(0)
                }
                val max = diff.max()
                if (max <= 0) continue
                val norm = GrayImage(gray.width, gray.height, IntArray(diff.size) { diff[it] * 255 / max })
                val positive = norm.pixels.filter { it > 0 }
                if (positive.isEmpty()) continue
                val tt = maxOf(Binarizer.otsu(GrayImage(positive.size, 1, positive.toIntArray())), 20)
                out += BooleanArray(diff.size) { norm.pixels[it] > tt }
            }
        }
        return out.filter { m -> m.count { it }.toFloat() / m.size in 0.02f..0.45f }
    }

    // --- manchas → peças ---

    private fun blobs(ink: BooleanArray, w: Int, h: Int, close: Float, minFrac: Float = 0.002f): List<Blob> {
        var mask = dilate(erode(ink, w, h, 1), w, h, 1)
        val n = w * h
        if (close > 0f) {
            val first = label(mask, w, h)
            val areas = first.areas.filter { it > 0.0005f * n }.sorted()
            if (areas.isEmpty()) return emptyList()
            val part = sqrt(areas[areas.size / 2].toFloat())
            val r = (part * close).toInt().coerceAtMost(15)
            if (r > 0) mask = erode(dilate(mask, w, h, r), w, h, r)
        }
        mask = fillHoles(mask, w, h)
        val labels = label(mask, w, h)
        val raw = labels.areas.indices.filter { labels.areas[it] > minFrac * n }.map { i ->
            val box = labels.boxes[i]
            val side = sqrt(labels.areas[i] / 4f)
            Triple(i, box, side)
        }
        val out = mutableListOf<Blob>()
        val pending = mutableListOf<Pair<Int, IntArray>>()
        for ((i, box, side) in raw) {
            val cells = cellsOf(labels.ids, w, box, i + 1, side)
            val piece = if (cells.size == 4) byCells[normalize(cells)] else null
            if (piece != null) {
                out += Blob((box[0] + box[2]) / 2f, (box[1] + box[3]) / 2f, piece, labels.areas[i], box)
            } else {
                pending += i to box
            }
        }
        // mancha que não é uma peça (peças fundidas, sujeira): conta como não reconhecida; peças
        // coladas de propósito são lidas por readChain
        for ((i, box) in pending) {
            out += Blob((box[0] + box[2]) / 2f, (box[1] + box[3]) / 2f, CommandPiece(null), labels.areas[i], box)
        }
        return out
    }

    /** As células de uma grade de blocos de lado [side] sobre a caixa da mancha que têm mais da metade de tinta. */
    private fun cellsOf(ids: IntArray, w: Int, box: IntArray, id: Int, side: Float): List<Cell> {
        val bw = box[2] - box[0]
        val bh = box[3] - box[1]
        val rows = maxOf(1, (bh / side).roundToInt())
        val cols = maxOf(1, (bw / side).roundToInt())
        if (rows * cols > 400) return emptyList()
        val cells = mutableListOf<Cell>()
        for (r in 0 until rows) for (c in 0 until cols) {
            val y0 = box[1] + r * bh / rows
            val y1 = maxOf(y0 + 1, box[1] + (r + 1) * bh / rows)
            val x0 = box[0] + c * bw / cols
            val x1 = maxOf(x0 + 1, box[0] + (c + 1) * bw / cols)
            var ink = 0
            for (y in y0 until y1) for (x in x0 until x1) if (ids[y * w + x] == id) ink++
            if (ink * 2 > (y1 - y0) * (x1 - x0)) cells += Cell(r, c)
        }
        return cells
    }

    /**
     * Divide células coladas em peças de 4, na ordem de leitura: a primeira célula que falta
     * (no eixo de leitura) é sempre de uma peça nova. Entre as divisões possíveis, fica a com menos
     * peças que não aparecem nos códigos do jogo (I, L, J: os códigos usam T, quadrado, S e Z) e,
     * depois, a em que as peças menos se sobrepõem ao longo do eixo.
     */
    private fun split(cells: List<Cell>, horizontal: Boolean): List<List<Cell>>? {
        val along = { c: Cell -> if (horizontal) c.col else c.row }
        val across = { c: Cell -> if (horizontal) c.row else c.col }
        val order = compareBy<Cell>({ along(it) }, { across(it) })
        var best: Pair<Int, List<List<Cell>>>? = null
        var calls = 0
        fun cost(pieces: List<List<Cell>>): Int {
            val odd = pieces.count { byCells.getValue(normalize(it)).shape !in CODE_SHAPES }
            val overlap = pieces.zipWithNext { a, b -> maxOf(0, a.maxOf(along) - b.minOf(along) + 1) }.sum()
            return odd * 1000 + overlap
        }
        fun rec(left: Set<Cell>, pieces: List<List<Cell>>) {
            if (++calls > 15_000 || best?.first == 0) return
            if (left.isEmpty()) {
                val c = cost(pieces)
                if (best == null || c < best!!.first) best = c to pieces
                return
            }
            val start = left.minWith(order)
            for (shape in placements) for (anchor in shape) {
                val placed = shape.map { Cell(start.row + it.row - anchor.row, start.col + it.col - anchor.col) }
                if (placed.all { it in left && order.compare(it, start) >= 0 }) rec(left - placed.toSet(), pieces + listOf(placed))
            }
        }
        rec(cells.toSet(), emptyList())
        return best?.second
    }

    /** Quantas peças da leitura não aparecem nos códigos do jogo. */
    private fun odd(reading: CommandReading) = reading.pieces.count { it.shape !in CODE_SHAPES }

    /** Os formatos que aparecem nos códigos do jogo (setas, pular, girar). */
    private val CODE_SHAPES = setOf(Tetromino.T, Tetromino.O, Tetromino.S, Tetromino.Z)

    // --- morfologia ---

    private class Labels(val ids: IntArray, val areas: IntArray, val boxes: List<IntArray>)

    /** Manchas ligadas (vizinhança de 4): id por pixel (0 = fundo), área e caixa [x0, y0, x1, y1). */
    private fun label(mask: BooleanArray, w: Int, h: Int): Labels {
        val ids = IntArray(w * h)
        val areas = mutableListOf<Int>()
        val boxes = mutableListOf<IntArray>()
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!mask[start] || ids[start] != 0) continue
            val id = areas.size + 1
            var top = 0
            stack[top++] = start
            ids[start] = id
            var area = 0
            val box = intArrayOf(w, h, 0, 0)
            while (top > 0) {
                val i = stack[--top]
                val x = i % w
                val y = i / w
                area++
                if (x < box[0]) box[0] = x
                if (y < box[1]) box[1] = y
                if (x + 1 > box[2]) box[2] = x + 1
                if (y + 1 > box[3]) box[3] = y + 1
                if (x > 0 && mask[i - 1] && ids[i - 1] == 0) { ids[i - 1] = id; stack[top++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && ids[i + 1] == 0) { ids[i + 1] = id; stack[top++] = i + 1 }
                if (y > 0 && mask[i - w] && ids[i - w] == 0) { ids[i - w] = id; stack[top++] = i - w }
                if (y < h - 1 && mask[i + w] && ids[i + w] == 0) { ids[i + w] = id; stack[top++] = i + w }
            }
            areas += area
            boxes += box
        }
        return Labels(ids, areas.toIntArray(), boxes)
    }

    /** Quantos pixels de tinta há em cada quadrado de lado 2r+1 (imagem integral). */
    private fun windowCount(mask: BooleanArray, w: Int, h: Int, r: Int, test: (count: Int, area: Int) -> Boolean): BooleanArray {
        val integral = IntArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0
            for (x in 0 until w) {
                if (mask[y * w + x]) row++
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + row
            }
        }
        return BooleanArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val x0 = (x - r).coerceAtLeast(0)
            val x1 = (x + r).coerceAtMost(w - 1)
            val y0 = (y - r).coerceAtLeast(0)
            val y1 = (y + r).coerceAtMost(h - 1)
            val count = integral[(y1 + 1) * (w + 1) + x1 + 1] - integral[y0 * (w + 1) + x1 + 1] -
                integral[(y1 + 1) * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
            test(count, (x1 - x0 + 1) * (y1 - y0 + 1))
        }
    }

    private fun dilate(mask: BooleanArray, w: Int, h: Int, r: Int) = windowCount(mask, w, h, r) { count, _ -> count > 0 }

    private fun erode(mask: BooleanArray, w: Int, h: Int, r: Int) = windowCount(mask, w, h, r) { count, area -> count == area }

    /** Preenche os buracos: fundo que não se liga à borda vira tinta. */
    private fun fillHoles(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val outside = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        fun push(i: Int) {
            if (!mask[i] && !outside[i]) { outside[i] = true; stack[top++] = i }
        }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (top > 0) {
            val i = stack[--top]
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return BooleanArray(w * h) { mask[it] || !outside[it] }
    }
}
