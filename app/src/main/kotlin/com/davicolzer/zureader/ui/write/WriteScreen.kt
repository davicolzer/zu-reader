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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.MiniGlyph
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.ZuWriter
import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.SharedLetters
import com.davicolzer.zureader.zu.ZuAlphabet
import com.davicolzer.zureader.zu.ZuGlyph
import com.davicolzer.zureader.zu.ZuNumerals

/**
 * Escrever à mão: o usuário toca nos desenhos dos glifos e monta a mensagem. Em cima, a mensagem
 * em Zu (colunas, como no jogo) e o texto em inglês; embaixo, o teclado de glifos.
 * "Ver resultado" abre a mensagem na tela de resultado (copiar, compartilhar, salvar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteScreen(
    draft: String,
    direction: ReadingDirection,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
) {
    var text by rememberSaveable(draft) { mutableStateOf(draft) }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // K/Q e U/V dividem o glifo: a letra vem do contexto do inglês e muda enquanto o usuário digita
            val message = SharedLetters.resolve(ZuWriter.normalize(text))
            ZuPreview(message, direction)
            // texto em inglês, com cursor
            Text(
                ReadingMode.MIXED.display(message) + "_",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = message },
            )

            // Quem digita não sabe o que os glifos significam: o teclado é organizado pelo desenho.
            // Letras: uma linha por forma, com as 4 rotações (K/Q e U/V numa tecla só); embaixo,
            // os números na mesma grade. Um glifo de cada.
            val numbers = (('0'..'9').toList() + ReadingMode.TEN).map { ZuNumerals.glyphFor(it) }
            val rows = ZuAlphabet.shapes + numbers.chunked(KEYS_PER_ROW)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { r, row ->
                    // um respiro entre as letras e os números
                    if (r == ZuAlphabet.shapes.size) Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { glyph -> GlyphKey(glyph, Modifier.weight(1f)) { text += glyph.primary } }
                        repeat(KEYS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionKey(stringResource(R.string.write_space), null, Modifier.weight(2f)) {
                    if (text.isNotEmpty() && !text.endsWith(' ')) text += ' '
                }
                ActionKey(stringResource(R.string.write_delete), R.drawable.ic_pixel_backspace, Modifier.weight(1f)) {
                    text = text.dropLast(1)
                }
                ActionKey(stringResource(R.string.write_clear), R.drawable.ic_pixel_trash, Modifier.weight(1f)) { text = "" }
            }

            if (ZuWriter.normalize(text).isNotEmpty()) {
                PixelPrimaryButton(stringResource(R.string.write_done), R.drawable.ic_pixel_check, onClick = { onDone(message) })
            }
        }
    }
}

/** As 4 rotações de cada forma numa linha. */
private const val KEYS_PER_ROW = 4

/** A mensagem desenhada em Zu, ajustada ao quadro; o último glifo em dourado. */
@Composable
private fun ZuPreview(message: String, direction: ReadingDirection) {
    val glyphs = remember(message, direction) { ZuWriter.layout(message, direction) }
    val ink = MaterialTheme.colorScheme.onBackground
    val last = ZuTheme.colors.gold
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ZuTheme.colors.ink),
        contentAlignment = Alignment.Center,
    ) {
        if (glyphs.isEmpty()) {
            Text(
                stringResource(R.string.write_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                val half = glyphs.first().size / 2
                val minX = glyphs.minOf { it.x } - half
                val maxX = glyphs.maxOf { it.x } + half
                val minY = glyphs.minOf { it.y } - half
                val maxY = glyphs.maxOf { it.y } + half
                // escala para caber, sem passar de 44 dp por glifo
                val scale = minOf(size.width / (maxX - minX), size.height / (maxY - minY), 44.dp.toPx() / (2 * half))
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

/** Tecla com o desenho do glifo e a letra (ou as duas letras, como K/Q) embaixo. */
@Composable
private fun GlyphKey(glyph: ZuGlyph, modifier: Modifier, onClick: () -> Unit) {
    val label = glyph.letters.map(ReadingMode.MIXED::symbolLabel).joinToString("/")
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MiniGlyph(glyph.pattern, MaterialTheme.colorScheme.onSurface, size = 34.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionKey(label: String, icon: Int?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
