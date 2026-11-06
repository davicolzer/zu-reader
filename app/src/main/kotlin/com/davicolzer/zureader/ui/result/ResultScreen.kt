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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import com.davicolzer.zureader.ui.correct.CorrectionSheet
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.MiniGlyph
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.ImageReading
import com.davicolzer.zureader.zu.GlyphReading
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuAlphabet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    image: Bitmap,
    result: ImageReading,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    savedId: Long? = null,
    onCorrect: (index: Int, letter: Char) -> Unit = { _, _ -> },
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    /** Volta para o recorte com os cantos usados; `null` se a leitura não veio de um recorte. */
    onAdjustCrop: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.result_saved_message)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.result_copied)
    val reading = result.reading
    val mode = result.mode
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
                actions = {
                    if (savedId != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_pixel_trash), stringResource(R.string.result_delete), Modifier.size(22.dp))
                        }
                    }
                },
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
            PhotoWithBoxes(bitmap, result, selected = editing, onTapGlyph = { editing = it })
            if (onAdjustCrop != null && reading.glyphs.isNotEmpty()) {
                PixelOutlinedButton(stringResource(R.string.result_adjust_crop), R.drawable.ic_pixel_crop, onAdjustCrop)
            }

            if (reading.glyphs.isEmpty()) {
                EmptyResult(onPickAnother, onAdjustCrop)
                return@Column
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (mode != ReadingMode.NUMBERS) R.string.result_text_label else R.string.result_numbers_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                ReadText(reading.text, reading.glyphs, mode)
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
                    stringResource(if (mode != ReadingMode.NUMBERS) R.string.result_glyphs_label else R.string.result_glyphs_label_numbers),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                GlyphStrip(reading.glyphs, reading.rotation, mode, onTap = { editing = it })
                val doubt = reading.glyphs.any { it.isLowConfidence }
                Text(
                    stringResource(
                        when {
                            mode == ReadingMode.NUMBERS && doubt -> R.string.result_low_confidence_numbers
                            mode == ReadingMode.NUMBERS -> R.string.result_tap_to_fix_numbers
                            doubt -> R.string.result_low_confidence
                            else -> R.string.result_tap_to_fix
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doubt) ZuTheme.colors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // K/Q e U/V são o mesmo glifo: avisa quando aparecem
                val pairs = if (mode == ReadingMode.NUMBERS) emptyList() else reading.glyphs
                    .mapNotNull { g -> ZuAlphabet.twin(g.letter)?.let { listOf(g.letter, it).sorted().joinToString(" e ") } }
                    .distinct().sorted()
                if (pairs.isNotEmpty()) {
                    Text(
                        stringResource(R.string.result_twin_letters, pairs.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // alturas iguais mesmo com fonte grande
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(R.drawable.ic_pixel_copy, stringResource(R.string.result_copy), Modifier.weight(1f)) {
                    copyText(context, mode.display(reading.text))
                    scope.launch { snackbar.showSnackbar(copied) }
                }
                ActionTile(R.drawable.ic_pixel_share, stringResource(R.string.result_share), Modifier.weight(1f)) {
                    shareText(context, mode.display(reading.text))
                }
                if (savedId == null) {
                    ActionTile(R.drawable.ic_pixel_save, stringResource(R.string.result_save), Modifier.weight(1f), primary = true) {
                        onSave()
                        scope.launch { snackbar.showSnackbar(savedMessage) }
                    }
                } else {
                    ActionTile(R.drawable.ic_pixel_check, stringResource(R.string.result_saved), Modifier.weight(1f), enabled = false) {}
                }
            }
        }
    }

    editing?.let { index ->
        reading.glyphs.getOrNull(index)?.let { glyph ->
            CorrectionSheet(
                glyph = glyph,
                rotation = reading.rotation,
                mode = mode,
                onSelect = { letter -> onCorrect(index, letter); editing = null },
                onDismiss = { editing = null },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.result_delete_title)) },
            text = { Text(stringResource(R.string.result_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.result_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}

/**
 * Foto com uma caixa em cada glifo (turquesa = certo, dourado = com dúvida) e a letra lida
 * em cima de cada um. Tocar num glifo da foto abre a correção dele; o selecionado fica em destaque.
 */
@Composable
private fun PhotoWithBoxes(bitmap: ImageBitmap, result: ImageReading, selected: Int?, onTapGlyph: (Int) -> Unit) {
    val teal = ZuTheme.colors.teal
    val gold = ZuTheme.colors.gold
    val labelText = MaterialTheme.colorScheme.background
    val highlight = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.photo_description)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.titleSmall
    val glyphs = result.reading.glyphs
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ZuTheme.colors.ink),
        contentAlignment = Alignment.Center,
    ) {
        val aspect = result.width / result.height.toFloat()
        val width = minOf(maxWidth, maxHeight * aspect)
        Box(Modifier.size(width, width / aspect).semantics { contentDescription = description }) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(glyphs) {
                        detectTapGestures { tap ->
                            val scale = size.width / result.width.toFloat()
                            val x = tap.x / scale
                            val y = tap.y / scale
                            // o glifo cuja caixa contém o toque (ou o mais perto, até meio glifo de distância)
                            val nearest = glyphs.indices.minByOrNull { i ->
                                val g = glyphs[i].source
                                kotlin.math.hypot(g.x - x, g.y - y)
                            } ?: return@detectTapGestures
                            val g = glyphs[nearest].source
                            if (kotlin.math.hypot(g.x - x, g.y - y) <= g.size) onTapGlyph(nearest)
                        }
                    },
            ) {
                val scale = size.width / result.width
                val stroke = Stroke(width = 2.dp.toPx())
                val thick = Stroke(width = 4.dp.toPx())
                glyphs.forEachIndexed { i, g ->
                    val cx = g.source.x * scale
                    val cy = g.source.y * scale
                    val halfW = g.source.width / 2 * scale + 3
                    val halfH = g.source.height / 2 * scale + 3
                    val color = if (g.isLowConfidence) gold else teal
                    rotate(result.skewDegrees, pivot = Offset(cx, cy)) {
                        drawRect(
                            color = if (i == selected) highlight else color,
                            topLeft = Offset(cx - halfW, cy - halfH),
                            size = Size(2 * halfW, 2 * halfH),
                            style = if (i == selected) thick else stroke,
                        )
                        // letra lida: etiqueta dentro da caixa, no canto de cima, proporcional ao glifo
                        val fontPx = (halfH * 0.6f).coerceIn(9.sp.toPx(), 18.sp.toPx())
                        val layout = measurer.measure(
                            result.mode.symbolLabel(g.letter) + if (g.isLowConfidence) "?" else "",
                            labelStyle.copy(fontSize = fontPx.toSp(), color = labelText),
                        )
                        val pad = 2.dp.toPx()
                        val boxW = layout.size.width + 2 * pad
                        val boxH = layout.size.height + pad
                        val left = cx - halfW
                        val top = cy - halfH
                        drawRoundRect(
                            color = if (i == selected) highlight else color,
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                        drawText(layout, topLeft = Offset(left + pad, top + pad / 2))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadText(text: String, glyphs: List<GlyphReading>, mode: ReadingMode) {
    val gold = ZuTheme.colors.gold
    // mesmo formato de ReadingMode.display, com as dúvidas em dourado
    val styled = buildAnnotatedString {
        var g = 0
        var afterSymbol = false
        var pendingSpace = false
        for (ch in text) {
            when (ch) {
                '\n' -> { append('\n'); afterSymbol = false; pendingSpace = false }
                ' ' -> if (mode != ReadingMode.NUMBERS) append(' ') else pendingSpace = afterSymbol
                else -> {
                    if (mode == ReadingMode.NUMBERS) {
                        if (pendingSpace) append(" / ") else if (afterSymbol) append(' ')
                    }
                    pendingSpace = false
                    afterSymbol = true
                    val low = glyphs.getOrNull(g++)?.isLowConfidence == true
                    val label = mode.symbolLabel(ch)
                    if (low) withStyle(SpanStyle(color = gold)) { append(label) } else append(label)
                }
            }
        }
    }
    Text(styled, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun GlyphStrip(glyphs: List<GlyphReading>, rotation: Int, mode: ReadingMode, onTap: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        glyphs.forEachIndexed { index, g ->
            val low = g.isLowConfidence
            val color = when {
                low -> ZuTheme.colors.gold
                g.corrected -> ZuTheme.colors.teal
                else -> MaterialTheme.colorScheme.onSurface
            }
            val label = stringResource(if (low) R.string.glyph_letter_doubt else R.string.glyph_letter, mode.symbolLabel(g.letter))
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (low) Modifier.border(BorderStroke(1.5.dp, ZuTheme.colors.gold), RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { onTap(index) }
                    .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .width(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // desenho na orientação em que foi lido
                MiniGlyph(g.source.bits.rotate(rotation), MaterialTheme.colorScheme.onSurface, size = 18.dp)
                Text(mode.symbolLabel(g.letter) + if (low) "?" else "", style = MaterialTheme.typography.titleSmall, color = color)
            }
        }
    }
}

@Composable
private fun ActionTile(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val content = when {
        !enabled -> ZuTheme.colors.teal
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        // encolhe para caber numa linha em vez de quebrar a palavra
        BasicText(
            label,
            style = MaterialTheme.typography.labelMedium.copy(color = content),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun EmptyResult(onPickAnother: () -> Unit, onAdjustCrop: (() -> Unit)?) {
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
    if (onAdjustCrop != null) {
        PixelPrimaryButton(stringResource(R.string.result_adjust_crop), R.drawable.ic_pixel_crop, onAdjustCrop)
        PixelOutlinedButton(stringResource(R.string.result_other_image), R.drawable.ic_pixel_gallery, onPickAnother)
    } else {
        PixelPrimaryButton(stringResource(R.string.result_other_image), R.drawable.ic_pixel_gallery, onPickAnother)
    }
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
