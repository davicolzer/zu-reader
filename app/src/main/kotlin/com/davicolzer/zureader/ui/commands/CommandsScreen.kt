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
import com.davicolzer.zureader.vision.CommandPiece
import com.davicolzer.zureader.vision.CommandReading
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
                val shown = remember(image) { image.asImageBitmap() }
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
                            reading.pieces.forEach { PieceTile(it) }
                        }
                        Text(stringResource(R.string.commands_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (reading.recognized < reading.pieces.size) {
                            Text(stringResource(R.string.commands_unknown), style = MaterialTheme.typography.bodySmall, color = ZuTheme.colors.gold)
                        }
                    }
                }
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
                        scope.launch { ShareCard.share(context, image, name, summary, description) }
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
 * A sequência em texto, para copiar, compartilhar e mostrar na coleção: o quadrado é "A" (pular);
 * as outras peças, a letra do formato (até a tabela de botões do jogo ser confirmada); "?" = não reconhecida.
 */
fun commandSummary(reading: CommandReading): String = reading.pieces.joinToString(" ") { piece ->
    when (piece.shape) {
        null -> "?"
        Tetromino.O -> "A"
        else -> piece.shape!!.name
    }
}

/** Uma peça: o desenho dos 4 blocos e, no quadrado, "A". Não reconhecida: "?" em dourado. */
@Composable
private fun PieceTile(piece: CommandPiece) {
    val label = when (piece.shape) {
        null -> stringResource(R.string.commands_piece_unknown)
        Tetromino.O -> stringResource(R.string.commands_piece_jump)
        else -> stringResource(R.string.commands_piece, piece.shape!!.name)
    }
    val known = piece.shape != null
    Column(
        Modifier
            .size(width = 56.dp, height = 68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, if (known) MaterialTheme.colorScheme.outline else ZuTheme.colors.gold, RoundedCornerShape(10.dp))
            .clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        if (known) {
            PieceDrawing(piece, MaterialTheme.colorScheme.onBackground, 34.dp)
            if (piece.shape == Tetromino.O) Text("A", style = MaterialTheme.typography.labelMedium, color = ZuTheme.colors.teal)
        } else {
            Text("?", style = MaterialTheme.typography.headlineSmall, color = ZuTheme.colors.gold)
        }
    }
}

/** O desenho de uma peça (os 4 blocos), centralizado num quadrado de lado [size]. */
@Composable
fun PieceDrawing(piece: CommandPiece, color: Color, size: Dp) {
    val cells = piece.cells
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
