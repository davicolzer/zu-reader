package com.davicolzer.zureader.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.MiniGlyph
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.ImageReading
import com.davicolzer.zureader.zu.GlyphReading
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    image: Bitmap,
    result: ImageReading,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.result_copied)
    val reading = result.reading
    val bitmap = remember(image) { image.asImageBitmap() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_pixel_back), stringResource(R.string.back), Modifier.size(22.dp))
                    }
                },
                title = { Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoWithBoxes(bitmap, result)

            if (reading.glyphs.isEmpty()) {
                EmptyResult(onPickAnother)
                return@Column
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.result_text_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReadText(reading.text, reading.glyphs)
                val count = reading.glyphs.size
                Text(
                    if (reading.rotation == 0) pluralStringResource(R.plurals.result_summary, count, count)
                    else pluralStringResource(R.plurals.result_summary_rotated, count, count, reading.rotation * 90),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.result_glyphs_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GlyphStrip(reading.glyphs, reading.rotation)
                if (reading.glyphs.any { it.isLowConfidence }) {
                    Text(
                        stringResource(R.string.result_low_confidence),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZuTheme.colors.gold,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(R.drawable.ic_pixel_copy, stringResource(R.string.result_copy), Modifier.weight(1f)) {
                    copyText(context, reading.text)
                    scope.launch { snackbar.showSnackbar(copied) }
                }
                ActionTile(R.drawable.ic_pixel_share, stringResource(R.string.result_share), Modifier.weight(1f)) {
                    shareText(context, reading.text)
                }
                ActionTile(R.drawable.ic_pixel_gallery, stringResource(R.string.result_other_image), Modifier.weight(1f), primary = true) {
                    onPickAnother()
                }
            }
        }
    }
}

/** Foto com uma caixa em cada glifo: turquesa = certo, dourado = com dúvida. */
@Composable
private fun PhotoWithBoxes(bitmap: ImageBitmap, result: ImageReading) {
    val teal = ZuTheme.colors.teal
    val gold = ZuTheme.colors.gold
    val description = stringResource(R.string.photo_description)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ZuTheme.colors.ink),
        contentAlignment = Alignment.Center,
    ) {
        val aspect = result.width / result.height.toFloat()
        val width = minOf(maxWidth, maxHeight * aspect)
        Box(Modifier.size(width, width / aspect).semantics { contentDescription = description }) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / result.width
                val stroke = Stroke(width = 2.dp.toPx())
                for (g in result.reading.glyphs) {
                    val half = g.source.size / 2 * scale
                    drawRect(
                        color = if (g.isLowConfidence) gold else teal,
                        topLeft = Offset(g.source.x * scale - half - 3, g.source.y * scale - half - 3),
                        size = Size(2 * half + 6, 2 * half + 6),
                        style = stroke,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadText(text: String, glyphs: List<GlyphReading>) {
    val gold = ZuTheme.colors.gold
    val styled = buildAnnotatedString {
        var g = 0
        for (ch in text) {
            if (ch == ' ') { append(ch); continue }
            val low = glyphs.getOrNull(g++)?.isLowConfidence == true
            if (low) withStyle(SpanStyle(color = gold)) { append(ch) } else append(ch)
        }
    }
    Text(styled, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun GlyphStrip(glyphs: List<GlyphReading>, rotation: Int) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (g in glyphs) {
            val low = g.isLowConfidence
            val color = if (low) ZuTheme.colors.gold else MaterialTheme.colorScheme.onSurface
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (low) Modifier.border(BorderStroke(1.5.dp, ZuTheme.colors.gold), RoundedCornerShape(10.dp)) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .width(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // desenho na orientação em que foi lido
                MiniGlyph(g.source.bits.rotate(rotation), MaterialTheme.colorScheme.onSurface, size = 18.dp)
                Text(if (low) "${g.letter}?" else g.letter.toString(), style = MaterialTheme.typography.titleSmall, color = color)
            }
        }
    }
}

@Composable
private fun ActionTile(@DrawableRes icon: Int, label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val background = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun EmptyResult(onPickAnother: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.result_empty_title), style = MaterialTheme.typography.titleSmall, color = ZuTheme.colors.gold)
        Text(stringResource(R.string.result_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PixelPrimaryButton(stringResource(R.string.result_other_image), R.drawable.ic_pixel_gallery, onPickAnother)
    Box(Modifier.height(8.dp))
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Zu", text))
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
