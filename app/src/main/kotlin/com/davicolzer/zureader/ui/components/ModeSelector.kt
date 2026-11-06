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
import com.davicolzer.zureader.zu.ReadingMode

/** Escolha entre ler a escrita (letras) ou os números do Zu. */
@Composable
fun ModeSelector(mode: ReadingMode, onMode: (ReadingMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = ReadingMode.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { onMode(m) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.tertiary,
                    activeContentColor = MaterialTheme.colorScheme.onTertiary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    stringResource(if (m == ReadingMode.TEXT) R.string.mode_text else R.string.mode_numbers),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
