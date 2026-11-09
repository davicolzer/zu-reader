package com.davicolzer.zureader.ui.correct

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.MiniGlyph
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.zu.GlyphReading
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuAlphabet
import com.davicolzer.zureader.zu.ZuNumerals
import com.davicolzer.zureader.zu.ZuMixed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState

/** Painel para corrigir a letra de um glifo. Tocar numa letra aplica e fecha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSheet(
    glyph: GlyphReading,
    rotation: Int,
    mode: ReadingMode = ReadingMode.TEXT,
    onSelect: (Char) -> Unit,
    onDismiss: () -> Unit,
    /** Tira o glifo do texto (não era letra); `null` esconde a opção. */
    onRemove: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        CorrectionContent(glyph, rotation, onSelect, mode, onRemove)
    }
}

@Composable
fun CorrectionContent(glyph: GlyphReading, rotation: Int, onSelect: (Char) -> Unit, mode: ReadingMode = ReadingMode.TEXT, onRemove: (() -> Unit)? = null) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        CorrectionBody(glyph, rotation, onSelect, mode, onRemove)
    }
}

@Composable
private fun CorrectionBody(glyph: GlyphReading, rotation: Int, onSelect: (Char) -> Unit, mode: ReadingMode, onRemove: (() -> Unit)?) {
    val gold = ZuTheme.colors.gold
    Column(
        // rola se não couber (tela baixa, ou letras e números juntos)
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(if (mode != ReadingMode.NUMBERS) R.string.correct_title else R.string.correct_title_numbers), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        mode == ReadingMode.NUMBERS && glyph.isLowConfidence -> stringResource(R.string.correct_body_doubt_numbers)
                        mode == ReadingMode.NUMBERS -> stringResource(R.string.correct_body_numbers, mode.symbolLabel(glyph.letter))
                        glyph.isLowConfidence -> stringResource(R.string.correct_body_doubt)
                        ZuAlphabet.twin(glyph.letter) != null ->
                            stringResource(R.string.correct_body_twin, glyph.letter.toString(), ZuAlphabet.twin(glyph.letter).toString())
                        else -> stringResource(R.string.correct_body, mode.symbolLabel(glyph.letter))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // não era letra (mancha, folha, cenário): botão pequeno, ao lado do glifo
                onRemove?.let {
                    PixelOutlinedButton(
                        stringResource(if (mode != ReadingMode.NUMBERS) R.string.correct_remove else R.string.correct_remove_number),
                        R.drawable.ic_pixel_trash,
                        onClick = it,
                        compact = true,
                    )
                }
            }
        }

        val twin = if (mode != ReadingMode.NUMBERS) ZuAlphabet.twin(glyph.letter) else null
        val suggestions = (listOfNotNull(glyph.letter, twin) + glyph.alternatives.map { it.letter })
            .filter { it in ReadingMode.MIXED.keyboard }.distinct().take(3)
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
                        MiniGlyph(ZuMixed.glyphFor(letter).pattern, if (current) ZuTheme.colors.teal else MaterialTheme.colorScheme.onSurface, size = 26.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(ReadingMode.MIXED.symbolLabel(letter), style = MaterialTheme.typography.titleMedium, color = if (current) ZuTheme.colors.teal else MaterialTheme.colorScheme.onSurface)
                        if (current) Text(stringResource(R.string.correct_current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // teclado pelo desenho: cada tecla mostra o glifo (e a letra pequena), para achar o igual
        // ao da foto; cada coluna é uma forma com as suas 4 rotações. Letras e números sempre: um
        // glifo lido como letra pode ser número (e vice-versa); na leitura de números, eles vêm primeiro
        val letters = (0..3).map { q -> ZuAlphabet.shapes.map { it[q] } }
        val numbers = ZuNumerals.glyphs.chunked(KEYS_PER_ROW)
        val groups = if (mode == ReadingMode.NUMBERS) listOf(numbers, letters) else listOf(letters, numbers)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((index, row) in (groups[0] + groups[1]).withIndex()) {
                if (index == groups[0].size) {
                    Box(Modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (key in row) {
                        // K/Q e U/V dividem o glifo: a tecla escolhe a primeira; a outra aparece nas sugestões
                        val label = key.letters.map(ReadingMode.MIXED::symbolLabel).joinToString("/")
                        Column(
                            Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .clickable { onSelect(key.primary) }
                                .semantics { contentDescription = label; role = Role.Button },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                        ) {
                            MiniGlyph(key.pattern, MaterialTheme.colorScheme.onSurface, size = 22.dp)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    repeat(KEYS_PER_ROW - row.size) { Box(Modifier.weight(1f).size(1.dp)) }
                }
            }
        }
    }
}

private const val KEYS_PER_ROW = 6
