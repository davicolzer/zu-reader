package com.davicolzer.zureader.vision

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
         * - S e Z = girar o mundo; qual é LT e qual é RT ainda não foi confirmado com a legenda do
         *   jogo: [swapRotation] troca (S = RT, Z = LT).
         * I, L e J (e peça não reconhecida) não têm botão: `null`.
         */
        fun of(piece: CommandPiece, swapRotation: Boolean = false): GameButton? = when (piece.shape) {
            Tetromino.O -> JUMP
            Tetromino.T -> when (piece.rotation) {
                0 -> DOWN
                1 -> LEFT
                2 -> UP
                else -> RIGHT
            }
            Tetromino.S -> if (swapRotation) ROTATE_RIGHT else ROTATE_LEFT
            Tetromino.Z -> if (swapRotation) ROTATE_LEFT else ROTATE_RIGHT
            else -> null
        }
    }
}

/** A sequência lida, na ordem de leitura (uma coluna já vem girada como se fosse uma linha). */
data class CommandReading(val pieces: List<CommandPiece>) {
    val code: String get() = pieces.joinToString(" ") { it.code }
    val recognized: Int get() = pieces.count { it.shape != null }

    companion object {
        fun parse(code: String) = CommandReading(code.split(' ').filter { it.isNotBlank() }.map(CommandPiece::parse))
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
        val chain = masks.mapNotNull { readChain(it, gray.width, gray.height) }.minByOrNull { it.first }?.second
        return chain ?: readSeparate(masks, gray.width, gray.height)
    }

    /** Peças separadas (cada uma é uma mancha). */
    private fun readSeparate(masks: List<BooleanArray>, width: Int, height: Int): CommandReading {
        var best: Candidate? = null
        for (mask in masks) {
            for (close in CLOSINGS) {
                val found = blobs(mask, width, height, close)
                val candidate = Candidate(found)
                if (best == null || candidate.score > best.score) best = candidate
            }
        }
        val found = best?.blobs ?: return CommandReading(emptyList())
        // manchas não reconhecidas bem menores que as peças são sujeira (um símbolo ao lado, um risco)
        val pieceArea = found.filter { it.piece.shape != null && it.area > 0 }.map { it.area }.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        val blobs = found.filter { it.piece.shape != null || it.area == 0 || it.area >= 0.6f * pieceArea }
        if (blobs.isEmpty()) return CommandReading(emptyList())
        val xs = blobs.map { it.cx }
        val ys = blobs.map { it.cy }
        val horizontal = (xs.max() - xs.min()) >= (ys.max() - ys.min())
        val ordered = blobs.sortedBy { if (horizontal) it.cx else it.cy }
        return CommandReading(ordered.map { if (horizontal) it.piece else turnCounterClockwise(it.piece) })
    }

    /**
     * Peças coladas numa linha só (o caso comum nas sequências do jogo): a maior mancha cobre quase
     * todo o comprimento do recorte. Ela é posta em pé (uma linha é girada ¼ de volta no sentido
     * horário), endireitada (cada fileira de pixels é deslocada pela inclinação do eixo) e coberta
     * por grades de 2 a 5 blocos de largura; fica a grade com as células mais nítidas (bem cheias
     * ou bem vazias) que se divide em peças de 4. Devolve a nitidez (menor = melhor) e a leitura.
     */
    private fun readChain(ink: BooleanArray, w: Int, h: Int): Pair<Float, CommandReading>? {
        val mask = fillHoles(dilate(erode(ink, w, h, 1), w, h, 1), w, h)
        val labels = label(mask, w, h)
        if (labels.areas.isEmpty()) return null
        val i = labels.areas.indices.maxBy { labels.areas[it] }
        val box = labels.boxes[i]
        val bw = box[2] - box[0]
        val bh = box[3] - box[1]
        if (maxOf(bw, bh) < 0.7f * maxOf(w, h) || minOf(bw, bh) < 4) return null
        // a mancha em pé: linha → girada ¼ de volta no sentido horário (o começo fica em cima)
        val horizontal = bw > bh
        val sh = if (horizontal) bw else bh
        val sw = if (horizontal) bh else bw
        val sub = BooleanArray(sw * sh) { k ->
            val r = k / sw
            val c = k % sw
            val (x, y) = if (horizontal) (box[0] + r) to (box[1] + bh - 1 - c) else (box[0] + c) to (box[1] + r)
            labels.ids[y * w + x] == i + 1
        }
        val (straight, sw2) = straighten(sub, sw, sh)
        // nitidez de cada grade: a soma, por célula, do quanto ela está longe de cheia ou vazia
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
        val grids = mutableListOf<Triple<Float, Int, List<Cell>>>()
        for (cols in 2..5) {
            val block = sw2.toFloat() / cols
            val minRows = maxOf(4, (sh / block / 2.5f).toInt())
            val maxRows = (sh / block * 1.6f).toInt() + 1
            for (rows in minRows..maxRows) {
                // depois de endireitada, os blocos são quase quadrados
                val aspect = (sh.toFloat() / rows) / block
                if (aspect < 0.7f || aspect > 1.45f) continue
                var crisp = 0f
                val cells = mutableListOf<Cell>()
                for (r in 0 until rows) for (c in 0 until cols) {
                    val f = fill(c * sw2 / cols, r * sh / rows, (c + 1) * sw2 / cols, (r + 1) * sh / rows)
                    crisp += minOf(f, 1 - f)
                    if (f > 0.5f) cells += Cell(r, c)
                }
                if (cells.size >= 8 && cells.size % 4 == 0) grids += Triple(crisp / (rows * cols), rows, cells)
            }
        }
        for ((crisp, _, cells) in grids.sortedBy { it.first }.take(12)) {
            if (crisp > MAX_CHAIN_BLUR) break
            val pieces = split(cells, horizontal = false) ?: continue
            // em pé, como uma coluna: cada peça é deitada de volta
            return crisp to CommandReading(pieces.map { turnCounterClockwise(byCells.getValue(normalize(it))) })
        }
        return null
    }

    /** Grades mais borradas que isso não são uma sequência de blocos. */
    private const val MAX_CHAIN_BLUR = 0.13f

    /** Endireita uma mancha em pé e inclinada: desloca cada fileira pela reta que passa pelos centros. */
    private fun straighten(mask: BooleanArray, w: Int, h: Int): Pair<BooleanArray, Int> {
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
        if (n < 2) return mask to w
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
        return tight to (x1 - x0)
    }

    /** A peça como fica com a coluna deitada (um quarto de volta no sentido anti-horário). */
    private fun turnCounterClockwise(piece: CommandPiece): CommandPiece {
        if (piece.shape == null) return piece
        val cells = rotateClockwise(rotateClockwise(rotateClockwise(piece.cells)))
        return byCells.getValue(cells)
    }

    private class Blob(val cx: Float, val cy: Float, val piece: CommandPiece, val area: Int = 0)

    private class Candidate(val blobs: List<Blob>) {
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

    private fun blobs(ink: BooleanArray, w: Int, h: Int, close: Float): List<Blob> {
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
        val raw = labels.areas.indices.filter { labels.areas[it] > 0.002f * n }.map { i ->
            val box = labels.boxes[i]
            val side = sqrt(labels.areas[i] / 4f)
            Triple(i, box, side)
        }
        // tamanho de bloco das peças inteiras, para dividir as coladas
        val singles = mutableListOf<Float>()
        val out = mutableListOf<Blob>()
        val pending = mutableListOf<Pair<Int, IntArray>>()
        for ((i, box, side) in raw) {
            val cells = cellsOf(labels.ids, w, box, i + 1, side)
            val piece = if (cells.size == 4) byCells[normalize(cells)] else null
            if (piece != null) {
                singles += side
                out += Blob((box[0] + box[2]) / 2f, (box[1] + box[3]) / 2f, piece, labels.areas[i])
            } else {
                pending += i to box
            }
        }
        val block = singles.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        for ((i, box) in pending) {
            val side = block ?: sqrt(labels.areas[i] / 4f)
            val cells = cellsOf(labels.ids, w, box, i + 1, side)
            val bw = (box[2] - box[0]).toFloat()
            val bh = (box[3] - box[1]).toFloat()
            val split = if (cells.size % 4 == 0 && cells.size in 8..40) split(cells, horizontal = bw >= bh) else null
            if (split == null) {
                out += Blob(box[0] + bw / 2, box[1] + bh / 2, CommandPiece(null), labels.areas[i])
            } else {
                val rows = maxOf(1, (bh / side).roundToInt())
                val cols = maxOf(1, (bw / side).roundToInt())
                for (p in split) {
                    val cx = box[0] + (p.map { it.col }.average().toFloat() + 0.5f) * bw / cols
                    val cy = box[1] + (p.map { it.row }.average().toFloat() + 0.5f) * bh / rows
                    out += Blob(cx, cy, byCells.getValue(normalize(p)))
                }
            }
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
     * (no eixo de leitura) é sempre de uma peça nova; fica a divisão em que as peças menos se
     * sobrepõem ao longo do eixo.
     */
    private fun split(cells: List<Cell>, horizontal: Boolean): List<List<Cell>>? {
        val along = { c: Cell -> if (horizontal) c.col else c.row }
        val across = { c: Cell -> if (horizontal) c.row else c.col }
        val order = compareBy<Cell>({ along(it) }, { across(it) })
        var best: Pair<Int, List<List<Cell>>>? = null
        var calls = 0
        fun overlap(pieces: List<List<Cell>>): Int = pieces.zipWithNext { a, b ->
            maxOf(0, a.maxOf(along) - b.minOf(along) + 1)
        }.sum()
        fun rec(left: Set<Cell>, pieces: List<List<Cell>>) {
            if (++calls > 20_000 || best?.first == 0) return
            if (left.isEmpty()) {
                val ov = overlap(pieces)
                if (best == null || ov < best!!.first) best = ov to pieces
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
