package com.davicolzer.zureader.ui.commands

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.ActionTile
import com.davicolzer.zureader.ui.components.AppTopBar
import com.davicolzer.zureader.ui.components.DeleteAction
import com.davicolzer.zureader.ui.components.DeleteDialog
import com.davicolzer.zureader.ui.components.DetailsFields
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.ui.components.ShareCard
import com.davicolzer.zureader.ui.components.ZoomableImage
import com.davicolzer.zureader.ui.components.copyText
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.Cell
import com.davicolzer.zureader.vision.CommandPiece
import com.davicolzer.zureader.vision.CommandReading
import com.davicolzer.zureader.vision.GameButton
import com.davicolzer.zureader.vision.Tetromino
import kotlinx.coroutines.launch

/**
 * Uma sequência de comandos lida (peças de 4 blocos): o nome em cima, a foto recortada, as peças
 * desenhadas na ordem de leitura e a descrição. Copiar, Compartilhar e Salvar ficam embaixo.
 * [onAdjustCrop] nulo = aberta da coleção (sem o recorte original).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandsScreen(
    reading: CommandReading,
    image: Bitmap,
    onBack: () -> Unit,
    onReadAnother: () -> Unit,
    savedId: Long? = null,
    onAdjustCrop: (() -> Unit)? = null,
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    name: String = "",
    description: String = "",
    onDetails: (name: String, description: String) -> Unit = { _, _ -> },
    /** Corrigir a sequência à mão (trocar, apagar ou acrescentar peças). */
    onFixByHand: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.result_copied)
    val saved = stringResource(R.string.result_saved_message)
    var confirmDelete by remember { mutableStateOf(false) }
    val found = reading.pieces.isNotEmpty()
    val summary = commandSummary(reading)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(R.string.commands_title, onBack) { if (savedId != null) DeleteAction { confirmDelete = true } } },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (found) DetailsFields(name, description, onDetails, showDescription = false)
                // a foto com cada peça marcada: o número na ordem de leitura e o botão
                val marked = remember(image, reading) { markPieces(image, reading) }
                val shown = remember(marked) { marked.asImageBitmap() }
                ZoomableImage(
                    shown,
                    stringResource(R.string.commands_image_description),
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZuTheme.colors.ink),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZuTheme.colors.ink)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!found) {
                        Text(stringResource(R.string.commands_none_title), style = MaterialTheme.typography.titleMedium, color = ZuTheme.colors.gold)
                        Text(stringResource(R.string.commands_none_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.commands_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            Text(
                                pluralStringResource(R.plurals.commands_count, reading.pieces.size, reading.pieces.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // as peças na ordem de leitura, quebrando a linha quando não cabem
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // cada peça na posição de leitura (uma coluna da foto aparece deitada aqui)
                            reading.pieces.forEachIndexed { i, piece -> PieceTile(i + 1, piece) }
                        }
                        Text(stringResource(R.string.commands_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (reading.recognized < reading.pieces.size) {
                            Text(stringResource(R.string.commands_unknown), style = MaterialTheme.typography.bodySmall, color = ZuTheme.colors.gold)
                        }
                    }
                }
                // a leitura errou? corrigir à mão, como um texto (também serve para acrescentar peças)
                PixelOutlinedButton(stringResource(R.string.commands_fix), R.drawable.ic_pixel_keyboard, onClick = onFixByHand)
                if (found) DetailsFields(name, description, onDetails, showName = false)
                if (onAdjustCrop != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelOutlinedButton(stringResource(R.string.commands_adjust), R.drawable.ic_pixel_crop, onClick = onAdjustCrop, modifier = Modifier.weight(1f), compact = true)
                        PixelOutlinedButton(stringResource(R.string.commands_another), R.drawable.ic_pixel_commands, onClick = onReadAnother, modifier = Modifier.weight(1f), compact = true)
                    }
                } else if (!found) {
                    PixelPrimaryButton(stringResource(R.string.commands_another), R.drawable.ic_pixel_commands, onClick = onReadAnother)
                }
            }
            if (found) {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionTile(R.drawable.ic_pixel_copy, stringResource(R.string.result_copy), Modifier.weight(1f)) {
                        copyText(context, summary)
                        scope.launch { snackbar.showSnackbar(copied) }
                    }
                    ActionTile(R.drawable.ic_pixel_share, stringResource(R.string.result_share), Modifier.weight(1f)) {
                        scope.launch { ShareCard.share(context, markPieces(image, reading), name, summary, description) }
                    }
                    if (savedId == null) {
                        ActionTile(R.drawable.ic_pixel_save, stringResource(R.string.result_save), Modifier.weight(1f), primary = true) {
                            onSave()
                            scope.launch { snackbar.showSnackbar(saved) }
                        }
                    } else {
                        ActionTile(R.drawable.ic_pixel_check, stringResource(R.string.result_saved), Modifier.weight(1f), enabled = false) {}
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteDialog(
            title = stringResource(R.string.commands_delete_title),
            body = stringResource(R.string.commands_delete_body),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * A sequência em texto, para copiar, compartilhar e mostrar na coleção: os botões do jogo
 * ("↑ ← LT A …"); peça sem botão, a letra do formato; "?" = não reconhecida.
 */
fun commandSummary(reading: CommandReading): String = reading.pieces.joinToString(" ") { piece ->
    GameButton.of(piece)?.label ?: piece.shape?.name ?: "?"
}

/** Uma peça: o desenho dos 4 blocos e, embaixo, o botão a apertar. Não reconhecida: "?" em dourado. */
@Composable
private fun PieceTile(number: Int, piece: CommandPiece) {
    val button = GameButton.of(piece)
    val label = when {
        piece.shape == null -> stringResource(R.string.commands_piece_unknown)
        button != null -> stringResource(button.spoken)
        else -> stringResource(R.string.commands_piece, piece.shape!!.name)
    }.let { "$number: $it" }
    val known = piece.shape != null
    Box(
        Modifier
            .size(width = 56.dp, height = 80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, if (known) MaterialTheme.colorScheme.outline else ZuTheme.colors.gold, RoundedCornerShape(10.dp))
            .clearAndSetSemantics { contentDescription = label },
    ) {
    // o número da peça, o mesmo da marca na foto
    Text(
        number.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 3.dp),
    )
    Column(
        Modifier.fillMaxSize().padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        if (known) {
            PieceDrawing(piece.cells, MaterialTheme.colorScheme.onBackground, 30.dp)
            Text(
                button?.label ?: piece.shape!!.name,
                // as setas são finas na fonte: maiores, para ficarem do peso de "LT" e "A"
                style = if (button?.label?.length == 1 && button != GameButton.JUMP) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = if (button != null) ZuTheme.colors.teal else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("?", style = MaterialTheme.typography.headlineSmall, color = ZuTheme.colors.gold)
        }
    }
    }
}

/**
 * A foto com cada peça marcada: um quadro em volta dela e uma etiqueta com o número (na ordem de
 * leitura) e o botão; não reconhecida, quadro dourado e "?". Sem as posições, a foto como está.
 */
fun markPieces(image: Bitmap, reading: CommandReading): Bitmap {
    if (reading.boxes.size != reading.pieces.size || reading.boxes.isEmpty()) return image
    val out = image.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)
    val side = minOf(out.width, out.height)
    val stroke = (maxOf(out.width, out.height) / 220f).coerceAtLeast(2f)
    val frame = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke }
    val tag = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = (maxOf(out.width, out.height) / 32f).coerceIn(12f, side * 0.5f)
    }
    reading.pieces.forEachIndexed { i, piece ->
        val b = reading.boxes[i]
        val r = android.graphics.RectF(b.left * out.width, b.top * out.height, b.right * out.width, b.bottom * out.height)
        val known = piece.shape != null
        val color = if (known) MARK_TEAL else MARK_GOLD
        frame.color = color
        canvas.drawRect(r, frame)
        val label = "${i + 1} " + (GameButton.of(piece)?.label ?: piece.shape?.name ?: "?")
        val pad = text.textSize * 0.25f
        val tw = text.measureText(label)
        // a etiqueta no canto de cima da peça, sem sair da foto
        val left = r.left.coerceIn(0f, (out.width - tw - 2 * pad).coerceAtLeast(0f))
        val top = (r.top - text.textSize - pad).coerceAtLeast(0f)
        tag.color = color
        canvas.drawRoundRect(left, top, left + tw + 2 * pad, top + text.textSize + pad, pad, pad, tag)
        text.color = MARK_INK
        canvas.drawText(label, left + pad, top + text.textSize * 0.92f, text)
    }
    return out
}

private const val MARK_TEAL = 0xFF4FC3C9.toInt()
private const val MARK_GOLD = 0xFFF5B700.toInt()
private const val MARK_INK = 0xFF14122A.toInt()

/** Como o leitor de tela fala cada botão. */
internal val GameButton.spoken: Int
    get() = when (this) {
        GameButton.UP -> R.string.button_up
        GameButton.DOWN -> R.string.button_down
        GameButton.LEFT -> R.string.button_left
        GameButton.RIGHT -> R.string.button_right
        GameButton.JUMP -> R.string.button_jump
        GameButton.ROTATE_LEFT -> R.string.button_lt
        GameButton.ROTATE_RIGHT -> R.string.button_rt
    }

/** O desenho de uma peça (os 4 blocos), centralizado num quadrado de lado [size]. */
@Composable
fun PieceDrawing(cells: List<Cell>, color: Color, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (cells.isEmpty()) return@Box
        Canvas(Modifier.size(size)) {
            val rows = cells.maxOf { it.row } + 1
            val cols = cells.maxOf { it.col } + 1
            val block = this.size.width / maxOf(rows, cols, 3)
            val gap = block * 0.12f
            val left = (this.size.width - cols * block) / 2
            val top = (this.size.height - rows * block) / 2
            for (c in cells) {
                drawRect(color, Offset(left + c.col * block + gap / 2, top + c.row * block + gap / 2), Size(block - gap, block - gap))
            }
        }
    }
}

/**
 * Uma sequência feita à mão (sem foto): a imagem com as peças desenhadas lado a lado (na posição
 * de leitura) e, embaixo de cada uma, o número e o botão. Quebra a linha a cada 8 peças.
 */
fun drawSequence(pieces: List<CommandPiece>): Bitmap {
    val block = 24f
    val gap = block
    val perRow = 8
    val label = 2 * block
    val cellW = 4 * block + gap
    val cellH = 4 * block + label + gap
    val rows = maxOf(1, (pieces.size + perRow - 1) / perRow)
    val cols = pieces.size.coerceIn(1, perRow)
    val width = (cols * cellW + gap).toInt()
    val height = (rows * cellH + gap).toInt()
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(MARK_INK)
    val paint = android.graphics.Paint().apply { color = 0xFFFFF6E5.toInt() }
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = MARK_TEAL
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = block * 1.1f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    pieces.forEachIndexed { i, piece ->
        val cells = piece.cells
        val pw = (cells.maxOf { it.col } + 1) * block
        val ph = (cells.maxOf { it.row } + 1) * block
        val cellLeft = gap + (i % perRow) * cellW
        val cellTop = gap + (i / perRow) * cellH
        val left = cellLeft + (4 * block - pw) / 2
        val top = cellTop + (4 * block - ph) / 2
        for (c in cells) canvas.drawRect(left + c.col * block + 1, top + c.row * block + 1, left + (c.col + 1) * block - 1, top + (c.row + 1) * block - 1, paint)
        val button = GameButton.of(piece)?.label ?: piece.shape?.name ?: "?"
        canvas.drawText("${i + 1} $button", cellLeft + 2 * block, cellTop + 4 * block + label * 0.75f, text)
    }
    return out
}
