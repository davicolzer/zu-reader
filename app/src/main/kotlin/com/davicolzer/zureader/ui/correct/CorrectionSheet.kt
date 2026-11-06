package com.davicolzer.zureader.ui.correct

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.MiniGlyph
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.zu.GlyphReading

/** Painel para corrigir a letra de um glifo. Tocar numa letra aplica e fecha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSheet(
    glyph: GlyphReading,
    rotation: Int,
    onSelect: (Char) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        CorrectionContent(glyph, rotation, onSelect)
    }
}

@Composable
fun CorrectionContent(glyph: GlyphReading, rotation: Int, onSelect: (Char) -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        CorrectionBody(glyph, rotation, onSelect)
    }
}

@Composable
private fun CorrectionBody(glyph: GlyphReading, rotation: Int, onSelect: (Char) -> Unit) {
    val gold = ZuTheme.colors.gold
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.correct_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(2.dp, if (glyph.isLowConfidence) gold else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                MiniGlyph(glyph.source.bits.rotate(rotation), MaterialTheme.colorScheme.onSurface, size = 54.dp)
            }
            Text(
                if (glyph.isLowConfidence) stringResource(R.string.correct_body_doubt)
                else stringResource(R.string.correct_body, glyph.letter.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val suggestions = (listOf(glyph.letter) + glyph.alternatives.map { it.letter })
            .filter { it in 'A'..'Z' }.distinct().take(3)
        if (suggestions.isNotEmpty()) {
            Text(stringResource(R.string.correct_suggestions), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEachIndexed { i, letter ->
                    val current = i == 0 && glyph.letter == letter
                    Column(
                        Modifier
                            .width(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.5.dp, if (current) ZuTheme.colors.teal else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { onSelect(letter) }
                            .semantics { role = Role.Button }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(letter.toString(), style = MaterialTheme.typography.headlineSmall, color = if (current) ZuTheme.colors.teal else MaterialTheme.colorScheme.onSurface)
                        if (current) Text(stringResource(R.string.correct_current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // teclado A–Z, 9 por linha
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ('A'..'Z').chunked(9).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (letter in row) {
                        val label = letter.toString()
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .clickable { onSelect(letter) }
                                .semantics { contentDescription = label; role = Role.Button },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    repeat(9 - row.size) { Box(Modifier.weight(1f).size(1.dp)) }
                }
            }
        }
    }
}
