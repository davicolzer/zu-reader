package com.davicolzer.zureader.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.davicolzer.zureader.R
import com.davicolzer.zureader.vision.ModeChoice

/** Escolha entre ler automático (escrita ou números, o que a imagem tiver), só a escrita ou só os números. */
@Composable
fun ModeSelector(mode: ModeChoice, onMode: (ModeChoice) -> Unit, modifier: Modifier = Modifier) {
    val modes = ModeChoice.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { onMode(m) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                // sem o ✓: a cor já mostra o escolhido e sobra espaço para o texto
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.tertiary,
                    activeContentColor = MaterialTheme.colorScheme.onTertiary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    stringResource(when (m) { ModeChoice.AUTO -> R.string.mode_auto; ModeChoice.TEXT -> R.string.mode_text; ModeChoice.NUMBERS -> R.string.mode_numbers }),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
