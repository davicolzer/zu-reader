package com.davicolzer.zureader.ui.note

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.theme.ZuTheme
import kotlin.math.roundToInt

/** Cores das anotações: dourado, vermelho, azul e branco (as do app, fortes em qualquer foto). */
private val MarkColors = listOf(Color(0xFFF5B700), Color(0xFFE5243B), Color(0xFF4FC3C9), Color.White)

/**
 * Editor de anotações: a foto ocupa o espaço livre; embaixo, as ferramentas (caneta, retângulo,
 * círculo, texto), as cores, desfazer e limpar. Os desenhos ficam em pixels da imagem, para
 * a foto salva sair igual ao que se vê. "Salvar" pede um nome (opcional).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotateScreen(
    image: Bitmap,
    title: String,
    onSave: (Bitmap, String) -> Unit,
    onBack: () -> Unit,
) {
    val marks = remember(image) { mutableStateListOf<Mark>() }
    var tool by remember { mutableStateOf(NoteTool.PEN) }
    var color by remember { mutableStateOf(MarkColors.first()) }
    // marca sendo desenhada agora (ainda com o dedo na tela)
    var drawing by remember { mutableStateOf<Mark?>(null) }
    var textAt by remember { mutableStateOf<Offset?>(null) }
    var naming by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.note_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    TextButton(onClick = { naming = true }) {
                        Icon(painterResource(R.drawable.ic_pixel_save), contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.note_save), style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink),
            ) {
                val density = LocalDensity.current
                val viewW = with(density) { maxWidth.toPx() }
                val viewH = with(density) { maxHeight.toPx() }
                // foto inteira visível, centralizada
                val scale = minOf(viewW / image.width, viewH / image.height)
                val offX = (viewW - image.width * scale) / 2
                val offY = (viewH - image.height * scale) / 2
                fun toImage(o: Offset) = Offset(
                    ((o.x - offX) / scale).coerceIn(0f, image.width.toFloat()),
                    ((o.y - offY) / scale).coerceIn(0f, image.height.toFloat()),
                )
                val bitmap = remember(image) { image.asImageBitmap() }
                val strokePx = MarkStyle.strokeWidth(image.width, image.height) * scale
                val textPx = MarkStyle.textSize(image.width, image.height) * scale
                val description = stringResource(R.string.note_canvas_description)

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .semantics { contentDescription = description }
                        .pointerInput(tool, color, scale) {
                            if (tool == NoteTool.TEXT) {
                                detectTapGestures { textAt = toImage(it) }
                            } else {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val p = toImage(start)
                                        drawing = when (tool) {
                                            NoteTool.PEN -> Mark.Stroke(listOf(p), color.toArgb())
                                            else -> Mark.Shape(p, p, ellipse = tool == NoteTool.ELLIPSE, color = color.toArgb())
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val p = toImage(change.position)
                                        drawing = when (val d = drawing) {
                                            is Mark.Stroke -> d.copy(points = d.points + p)
                                            is Mark.Shape -> d.copy(to = p)
                                            else -> d
                                        }
                                    },
                                    onDragEnd = { drawing?.let { marks += it }; drawing = null },
                                    onDragCancel = { drawing = null },
                                )
                            }
                        },
                ) {
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset(offX.roundToInt(), offY.roundToInt()),
                        dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
                    )
                    fun view(p: Offset) = Offset(p.x * scale + offX, p.y * scale + offY)
                    val style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = textPx
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        setShadowLayer(strokePx, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    for (mark in marks + listOfNotNull(drawing)) {
                        val c = Color(mark.color)
                        when (mark) {
                            is Mark.Stroke -> {
                                val path = Path()
                                mark.points.firstOrNull()?.let { view(it).let { v -> path.moveTo(v.x, v.y) } }
                                mark.points.drop(1).forEach { view(it).let { v -> path.lineTo(v.x, v.y) } }
                                if (mark.points.size == 1) drawCircle(c, strokePx / 2, view(mark.points[0]))
                                else drawPath(path, c, style = style)
                            }
                            is Mark.Shape -> {
                                val a = view(mark.from)
                                val b = view(mark.to)
                                val topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
                                val size = Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y))
                                if (mark.ellipse) drawOval(c, topLeft, size, style = style) else drawRect(c, topLeft, size, style = style)
                            }
                            is Mark.Label -> {
                                textPaint.color = mark.color
                                val at = view(mark.at)
                                var y = at.y - textPaint.ascent()
                                for (row in mark.text.lines()) {
                                    drawContext.canvas.nativeCanvas.drawText(row, at.x, y, textPaint)
                                    y += textPaint.fontSpacing
                                }
                            }
                        }
                    }
                }
            }

            // ferramentas
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(R.drawable.ic_pixel_pen, stringResource(R.string.note_tool_pen), tool == NoteTool.PEN, Modifier.weight(1f)) { tool = NoteTool.PEN }
                ToolButton(R.drawable.ic_pixel_rect, stringResource(R.string.note_tool_rect), tool == NoteTool.RECTANGLE, Modifier.weight(1f)) { tool = NoteTool.RECTANGLE }
                ToolButton(R.drawable.ic_pixel_circle, stringResource(R.string.note_tool_circle), tool == NoteTool.ELLIPSE, Modifier.weight(1f)) { tool = NoteTool.ELLIPSE }
                ToolButton(R.drawable.ic_pixel_text, stringResource(R.string.note_tool_text), tool == NoteTool.TEXT, Modifier.weight(1f)) { tool = NoteTool.TEXT }
            }
            // cores, desfazer e limpar
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MarkColors.forEach { c ->
                    val selected = c == color
                    Spacer(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(role = Role.RadioButton) { color = c }
                            .semantics { this.selected = selected },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }, enabled = marks.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_pixel_undo), stringResource(R.string.note_undo), Modifier.size(22.dp))
                }
                IconButton(onClick = { marks.clear() }, enabled = marks.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_pixel_trash), stringResource(R.string.note_clear), Modifier.size(22.dp))
                }
            }
            Text(
                stringResource(if (tool == NoteTool.TEXT) R.string.note_hint_text else R.string.note_hint_draw),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    textAt?.let { at ->
        var value by remember(at) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textAt = null },
            title = { Text(stringResource(R.string.note_text_title)) },
            text = { OutlinedTextField(value, { value = it }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    if (value.isNotBlank()) marks += Mark.Label(at, value.trim(), color.toArgb())
                    textAt = null
                }) { Text(stringResource(R.string.note_text_add)) }
            },
            dismissButton = { TextButton(onClick = { textAt = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (naming) {
        var name by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.note_name_title)) },
            text = {
                OutlinedTextField(
                    name,
                    { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.note_default_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    onSave(renderMarks(image, marks.toList()), name.trim())
                }) { Text(stringResource(R.string.note_save)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ToolButton(icon: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        val tint = if (selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
