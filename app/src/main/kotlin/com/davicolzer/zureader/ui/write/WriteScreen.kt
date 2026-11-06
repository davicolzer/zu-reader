package com.davicolzer.zureader.ui.write

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.MiniGlyph
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.ZuWriter
import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.SharedLetters
import com.davicolzer.zureader.zu.ZuAlphabet
import com.davicolzer.zureader.zu.ZuGlyph
import com.davicolzer.zureader.zu.ZuMixed
import com.davicolzer.zureader.zu.ZuNumerals

/**
 * Escrever à mão, tudo numa tela só (sem rolagem). Em cima, a mensagem em Zu (colunas, como no
 * jogo) e o texto em inglês, ocupando o espaço que sobra; embaixo, o teclado:
 * - **Glifos**: quem copia do jogo não sabe o que os glifos significam, então eles vêm pelo
 *   desenho: duas formas por linha (4 rotações cada; K/Q e U/V numa tecla só, letra decidida pelo
 *   inglês) e, embaixo, todos os desenhos de números (com as variantes de 3 a 7).
 * - **Letras e números**: A–Z e 0–10, um de cada (K e Q separados); cada tecla mostra o glifo.
 * "Pronto" abre a mensagem na tela de resultado (copiar, compartilhar, salvar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteScreen(
    draft: String,
    direction: ReadingDirection,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    startWithLetters: Boolean = false,
) {
    var text by rememberSaveable(draft) { mutableStateOf(draft) }
    var byLetters by rememberSaveable { mutableStateOf(startWithLetters) }
    // K/Q e U/V tocados pelo glifo: a letra vem do contexto do inglês e muda enquanto o usuário digita
    val message = ZuWriter.normalize(SharedLetters.resolve(text))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_pixel_back), stringResource(R.string.back), Modifier.size(22.dp))
                    }
                },
                title = { Text(stringResource(R.string.write_title), style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // mensagem: ocupa o espaço que o teclado deixa
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ZuPreview(message, direction, Modifier.fillMaxWidth().weight(1f))
                Text(
                    ReadingMode.MIXED.display(message) + "_",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = message },
                )
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { i, letters ->
                    SegmentedButton(
                        selected = byLetters == letters,
                        onClick = { byLetters = letters },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.tertiary,
                            activeContentColor = MaterialTheme.colorScheme.onTertiary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(stringResource(if (letters) R.string.write_by_letters else R.string.write_by_glyphs), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (!byLetters) {
                // duas formas por linha (4 + 4 rotações) e, embaixo, todos os desenhos de números
                val letterRows = ZuAlphabet.shapes.chunked(2).map { it.flatten() }
                val numberRows = ZuNumerals.glyphs.chunked(PER_ROW)
                KeyGrid(letterRows, numberRows) { glyph, modifier ->
                    GlyphKey(glyph, modifier) { text += SharedLetters.typed(glyph) }
                }
            } else {
                // um de cada, na ordem: A–Z e depois 0–10
                val symbols = ('A'..'Z').toList() + ('0'..'9').toList() + ReadingMode.TEN
                KeyGrid(symbols.chunked(PER_ROW), emptyList()) { symbol, modifier ->
                    LetterKey(symbol, modifier) { text += symbol }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionKey(stringResource(R.string.write_space), null, Modifier.weight(1.4f)) {
                    if (text.isNotEmpty() && !text.endsWith(' ')) text += ' '
                }
                ActionKey(stringResource(R.string.write_delete), R.drawable.ic_pixel_backspace, Modifier.weight(1f)) {
                    text = text.dropLast(1)
                }
                ActionKey(stringResource(R.string.write_clear), R.drawable.ic_pixel_trash, Modifier.weight(1f)) { text = "" }
                ActionKey(
                    stringResource(R.string.write_done),
                    R.drawable.ic_pixel_check,
                    Modifier.weight(1.6f),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    showLabel = true,
                    enabled = message.isNotEmpty(),
                ) { onDone(message) }
            }
        }
    }
}

/** Teclas por linha. */
private const val PER_ROW = 8

/** Grade de teclas: [top] e, depois de um respiro, [bottom] (as linhas incompletas são completadas com vazio). */
@Composable
private fun <T> KeyGrid(top: List<List<T>>, bottom: List<List<T>>, key: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((index, row) in (top + bottom).withIndex()) {
            if (index == top.size && bottom.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { key(it, Modifier.weight(1f)) }
                repeat(PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A mensagem desenhada em Zu, ajustada ao espaço; o último glifo em dourado. */
@Composable
private fun ZuPreview(message: String, direction: ReadingDirection, modifier: Modifier) {
    val glyphs = remember(message, direction) { ZuWriter.layout(message, direction) }
    val ink = MaterialTheme.colorScheme.onBackground
    val last = ZuTheme.colors.gold
    Box(modifier, contentAlignment = Alignment.Center) {
        if (glyphs.isEmpty()) {
            Text(
                stringResource(R.string.write_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val half = glyphs.first().size / 2
                val minX = glyphs.minOf { it.x } - half
                val maxX = glyphs.maxOf { it.x } + half
                val minY = glyphs.minOf { it.y } - half
                val maxY = glyphs.maxOf { it.y } + half
                // escala para caber, sem passar de 40 dp por glifo
                val scale = minOf(size.width / (maxX - minX), size.height / (maxY - minY), 40.dp.toPx() / (2 * half))
                val offX = (size.width - (maxX - minX) * scale) / 2
                val offY = (size.height - (maxY - minY) * scale) / 2
                val cell = 2 * half * scale / 5
                glyphs.forEachIndexed { i, g ->
                    val left = offX + (g.x - half - minX) * scale
                    val top = offY + (g.y - half - minY) * scale
                    val color = if (i == glyphs.lastIndex) last else ink
                    for (r in 0 until 5) for (c in 0 until 5) if (g.bits[r, c]) {
                        drawRect(color, Offset(left + c * cell, top + r * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }
        }
    }
}

private val KeyShape = RoundedCornerShape(10.dp)

/** Tecla com o desenho do glifo e, pequena, a letra (ou as duas, como K/Q) embaixo. */
@Composable
private fun GlyphKey(glyph: ZuGlyph, modifier: Modifier, onClick: () -> Unit) {
    val label = glyph.letters.map(ReadingMode.MIXED::symbolLabel).joinToString("/")
    KeyBox(label, modifier, onClick) {
        MiniGlyph(glyph.pattern, MaterialTheme.colorScheme.onSurface, size = 22.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Tecla de letra (ou número): a letra e, pequeno, o glifo dela em Zu. */
@Composable
private fun LetterKey(symbol: Char, modifier: Modifier, onClick: () -> Unit) {
    val label = ReadingMode.MIXED.symbolLabel(symbol)
    KeyBox(label, modifier, onClick) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        MiniGlyph(ZuMixed.glyphFor(symbol).pattern, MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp)
    }
}

@Composable
private fun KeyBox(label: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier
            .height(KEY_HEIGHT)
            .clip(KeyShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, KeyShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) { content() }
}

/** Altura das teclas: alvo de toque ≥ 48 dp. */
private val KEY_HEIGHT = 50.dp

@Composable
private fun ActionKey(
    label: String,
    icon: Int?,
    modifier: Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    showLabel: Boolean = icon == null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) container else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant
        if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        if (showLabel) Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}
