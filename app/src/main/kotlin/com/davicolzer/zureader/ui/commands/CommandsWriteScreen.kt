package com.davicolzer.zureader.ui.commands

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.ActionKey
import com.davicolzer.zureader.ui.components.AppTopBar
import com.davicolzer.zureader.ui.components.CursorEdit
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.CommandPiece
import com.davicolzer.zureader.vision.GameButton
import com.davicolzer.zureader.vision.Tetromino

/** As 7 teclas: cada botão do jogo e a peça que o representa (na posição de leitura). */
val COMMAND_KEYS: List<CommandPiece> = listOf(
    CommandPiece(Tetromino.T, 2), CommandPiece(Tetromino.T, 0), CommandPiece(Tetromino.T, 1), CommandPiece(Tetromino.T, 3),
    CommandPiece(Tetromino.O, 0), CommandPiece(Tetromino.Z, 0), CommandPiece(Tetromino.S, 0),
)

/**
 * Escrever (ou corrigir) uma sequência de comandos à mão, como um texto: a sequência em cima, com
 * o cursor (barra) onde a próxima peça entra; tocar numa peça seleciona para trocar ou apagar;
 * ◀ ▶ movem o cursor. Embaixo, os 7 botões do jogo, cada um com a peça que o representa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandsWriteScreen(
    initial: List<CommandPiece>,
    onDone: (List<CommandPiece>) -> Unit,
    onBack: () -> Unit,
    /** Peça já selecionada ao abrir (prévias). */
    startSelected: Int? = null,
) {
    var edit by remember(initial) { mutableStateOf(CursorEdit(initial, initial.size, startSelected)) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(R.string.commands_write_title, onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // a sequência: rola se crescer; o cursor é uma barra entre as peças
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                if (edit.items.isEmpty()) {
                    Text(
                        stringResource(R.string.commands_write_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    edit.items.forEachIndexed { i, piece ->
                        if (edit.cursor == i && edit.selected == null) Caret()
                        EditTile(i + 1, piece, selected = edit.selected == i) { edit = edit.select(i) }
                    }
                    // o fim da sequência: tocar põe o cursor lá
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 70.dp)
                            .clickable(role = Role.Button) { edit = edit.moveTo(edit.items.size) },
                        contentAlignment = Alignment.CenterStart,
                    ) { if (edit.cursor == edit.items.size && edit.selected == null) Caret() }
                }
            }

            // os 7 botões: setas em cima; pular e girar embaixo
            COMMAND_KEYS.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { piece -> CommandKey(piece, Modifier.weight(1f)) { edit = edit.type(piece) } }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionKey(stringResource(R.string.cursor_left), R.drawable.ic_pixel_cursor_left, Modifier.weight(1f)) { edit = edit.left() }
                ActionKey(stringResource(R.string.cursor_right), R.drawable.ic_pixel_cursor_right, Modifier.weight(1f)) { edit = edit.right() }
                ActionKey(stringResource(R.string.write_delete), R.drawable.ic_pixel_backspace, Modifier.weight(1f)) { edit = edit.delete() }
                ActionKey(stringResource(R.string.write_clear), R.drawable.ic_pixel_trash, Modifier.weight(1f)) { edit = edit.clear() }
                ActionKey(
                    stringResource(R.string.write_done),
                    R.drawable.ic_pixel_check,
                    Modifier.weight(1.9f),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    showLabel = true,
                    enabled = edit.items.isNotEmpty(),
                ) { onDone(edit.items) }
            }
        }
    }
}

/** O cursor: uma barra dourada entre as peças. */
@Composable
private fun Caret() {
    Box(Modifier.padding(horizontal = 2.dp).width(3.dp).height(70.dp).clip(RoundedCornerShape(2.dp)).background(ZuTheme.colors.gold))
}

/** Uma peça da sequência: o número, o desenho e o botão; selecionada, com borda dourada. */
@Composable
private fun EditTile(number: Int, piece: CommandPiece, selected: Boolean, onClick: () -> Unit) {
    val button = GameButton.of(piece)
    val label = "$number: " + (button?.let { stringResource(it.spoken) } ?: piece.shape?.name ?: "?")
    Box(
        Modifier
            .size(width = 48.dp, height = 70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(if (selected) 2.dp else 1.dp, if (selected) ZuTheme.colors.gold else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
        )
        Column(
            Modifier.fillMaxSize().padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            PieceDrawing(piece.cells, MaterialTheme.colorScheme.onBackground, 24.dp)
            val tileLabel = button?.label ?: piece.shape?.name ?: "?"
            Text(tileLabel, style = if (tileLabel.length == 1 && button != GameButton.JUMP) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge, color = ZuTheme.colors.teal)
        }
    }
}

/** Tecla de um botão do jogo: a peça e o nome do botão. */
@Composable
private fun CommandKey(piece: CommandPiece, modifier: Modifier, onClick: () -> Unit) {
    val button = GameButton.of(piece)!!
    val spoken = stringResource(button.spoken)
    Column(
        modifier
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = spoken, onClick = onClick)
            .semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        PieceDrawing(piece.cells, MaterialTheme.colorScheme.onSurface, 26.dp)
        // as setas são finas na fonte: maiores, para ficarem do peso de "LT" e "A"
        Text(button.label, style = if (button.label.length == 1 && button != GameButton.JUMP) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall, color = ZuTheme.colors.teal)
    }
}
