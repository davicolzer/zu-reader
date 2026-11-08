package com.davicolzer.zureader.ui.note

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.unit.sp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.theme.ZuTheme
import kotlin.math.roundToInt
import com.davicolzer.zureader.ui.components.AppTopBar
import com.davicolzer.zureader.ui.theme.CubeGold
import com.davicolzer.zureader.ui.theme.ScanTeal

/** Cores das anotações: dourado, vermelho, azul e branco (as do app, fortes em qualquer foto). */
private val MarkColors = listOf(CubeGold, Color(0xFFE5243B), ScanTeal, Color.White)

/**
 * Editor de anotações (abre direto ao escolher a foto): em cima, "Recortar" e "OK"; a foto ocupa
 * o espaço livre; embaixo, as ferramentas (mover, seta, retângulo, círculo, texto), as cores, desfazer e apagar. Com
 * "Mover", tocar num desenho seleciona: arrastar move, uma cor troca a cor e a lixeira apaga só
 * ele. Os desenhos ficam em pixels da imagem, para a foto salva sair igual ao que se vê.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotateScreen(
    image: Bitmap,
    /** "OK": a foto com os desenhos e os desenhos. */
    onDone: (Bitmap, List<Mark>) -> Unit,
    /** "Recortar": abre o recorte da foto, com os desenhos feitos até agora. */
    onCrop: (List<Mark>) -> Unit,
    onBack: () -> Unit,
    /** Desenhos que já estavam na foto (ao anotar mais): continuam editáveis. */
    marks: List<Mark> = emptyList(),
    /** Ferramenta e desenho selecionado ao abrir (prévias). */
    startTool: NoteTool = NoteTool.ARROW,
    startSelected: Int? = null,
    /** Texto já existente aberto para editar na foto ao abrir (prévias). */
    startEditing: Int? = null,
) {
    val initialMarks = marks
    val marks = remember(image) { mutableStateListOf<Mark>().apply { addAll(initialMarks) } }
    // como os desenhos estavam antes de cada mudança, para desfazer (desenhar, mover, apagar, cor)
    val undo = remember(image) { mutableStateListOf<List<Mark>>() }
    fun change(block: () -> Unit) {
        undo += marks.toList()
        block()
    }
    var tool by remember { mutableStateOf(startTool) }
    var color by remember { mutableStateOf(MarkColors.first()) }
    // marca sendo desenhada agora (ainda com o dedo na tela)
    var drawing by remember { mutableStateOf<Mark?>(null) }
    // texto sendo escrito direto na foto: onde ([TextEdit.at]) e qual texto já existente ([TextEdit.index])
    val startLabel = startEditing?.let { initialMarks.getOrNull(it) as? Mark.Label }
    var textEdit by remember { mutableStateOf(startLabel?.let { TextEdit(it.at, startEditing) }) }
    var textValue by remember { mutableStateOf(TextFieldValue(startLabel?.text.orEmpty(), TextRange(startLabel?.text?.length ?: 0))) }
    // termina o texto em edição: novo entra, mudado troca, apagado sai (tudo com "desfazer")
    fun commitText() {
        val e = textEdit ?: return
        val t = textValue.text.trim()
        val old = e.index?.let { marks.getOrNull(it) as? Mark.Label }
        when {
            old != null && t.isEmpty() -> change { marks.removeAt(e.index) }
            old != null && old.text != t -> change { marks[e.index] = old.copy(text = t) }
            old == null && t.isNotEmpty() -> change { marks += Mark.Label(e.at, t, color.toArgb()) }
        }
        textEdit = null
    }
    BackHandler(enabled = textEdit != null) { commitText() }
    var selected by remember { mutableStateOf(startSelected) }
    if (tool != NoteTool.MOVE || selected?.let { it !in marks.indices } == true) selected = null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(R.string.note_title, onBack) {
                TextButton(onClick = { commitText(); onCrop(marks.toList()) }) {
                    Icon(painterResource(R.drawable.ic_pixel_crop), contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.note_crop), style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { commitText(); onDone(renderMarks(image, marks.toList()), marks.toList()) }) {
                    Icon(painterResource(R.drawable.ic_pixel_check), contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.note_done), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                // com o teclado aberto (escrevendo na foto), a foto encolhe e continua à vista
                .imePadding()
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
                val textImagePx = MarkStyle.textSize(image.width, image.height)
                // folga para acertar um desenho com o dedo: ~20 dp, em pixels da imagem
                val tolerance = maxOf(MarkStyle.strokeWidth(image.width, image.height) * 2, with(density) { 20.dp.toPx() } / scale)
                val description = stringResource(R.string.note_canvas_description)
                val selectionColor = MaterialTheme.colorScheme.onBackground

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .semantics { contentDescription = description }
                        .pointerInput(tool, color, scale) {
                            if (tool == NoteTool.MOVE) {
                                detectTapGestures { selected = hitMark(marks, toImage(it), tolerance, textImagePx) }
                            }
                        }
                        .pointerInput(tool, color, scale) {
                            if (tool == NoteTool.TEXT) {
                                // tocar escreve ali; tocar num texto muda ele; escrevendo, tocar fora termina
                                detectTapGestures { tap ->
                                    if (textEdit != null) {
                                        commitText()
                                        return@detectTapGestures
                                    }
                                    val p = toImage(tap)
                                    val hit = hitMark(marks, p, tolerance, textImagePx)?.takeIf { marks[it] is Mark.Label }
                                    val label = hit?.let { marks[it] as Mark.Label }
                                    textValue = TextFieldValue(label?.text.orEmpty(), TextRange(label?.text?.length ?: 0))
                                    textEdit = TextEdit(label?.at ?: p, hit)
                                }
                            } else if (tool == NoteTool.MOVE) {
                                // arrastar um desenho: seleciona e move (um "desfazer" por arrasto)
                                var moving: Int? = null
                                detectDragGestures(
                                    onDragStart = { start ->
                                        moving = hitMark(marks, toImage(start), tolerance, textImagePx)
                                        moving?.let { undo += marks.toList() }
                                        selected = moving
                                    },
                                    onDrag = { change, amount ->
                                        val i = moving ?: return@detectDragGestures
                                        change.consume()
                                        marks[i] = marks[i].moved(amount / scale)
                                    },
                                    onDragEnd = { moving = null },
                                    onDragCancel = { moving = null },
                                )
                            } else {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val p = toImage(start)
                                        drawing = when (tool) {
                                            NoteTool.ARROW -> Mark.Arrow(p, p, color.toArgb())
                                            else -> Mark.Shape(p, p, ellipse = tool == NoteTool.ELLIPSE, color = color.toArgb())
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val p = toImage(change.position)
                                        drawing = when (val d = drawing) {
                                            is Mark.Arrow -> d.copy(to = p)
                                            is Mark.Shape -> d.copy(to = p)
                                            else -> d
                                        }
                                    },
                                    onDragEnd = {
                                        // um toque sem arrastar não vira desenho
                                        drawing?.takeIf { it.bounds(textImagePx).maxDimension > tolerance / 2 }?.let { d -> change { marks += d } }
                                        drawing = null
                                    },
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
                    // o texto em edição aparece no campo por cima, não aqui
                    val hidden = textEdit?.index?.let { marks.getOrNull(it) }
                    for (mark in marks + listOfNotNull(drawing)) {
                        if (mark === hidden) continue
                        val c = Color(mark.color)
                        when (mark) {
                            is Mark.Arrow -> {
                                val a = view(mark.from)
                                val b = view(mark.to)
                                val (l, r) = arrowHead(a, b, strokePx)
                                val path = Path().apply {
                                    moveTo(a.x, a.y)
                                    lineTo(b.x, b.y)
                                    moveTo(l.x, l.y)
                                    lineTo(b.x, b.y)
                                    lineTo(r.x, r.y)
                                }
                                drawPath(path, c, style = style)
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
                    // desenho selecionado: moldura tracejada em volta
                    selected?.let { marks.getOrNull(it) }?.let { mark ->
                        val b = mark.bounds(textImagePx)
                        val pad = strokePx * 1.5f + 4.dp.toPx()
                        val a = view(Offset(b.left, b.top)) - Offset(pad, pad)
                        val c = view(Offset(b.right, b.bottom)) + Offset(pad, pad)
                        drawRect(
                            selectionColor,
                            a,
                            Size(c.x - a.x, c.y - a.y),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                        )
                    }
                }

                // escrevendo direto na foto: o campo fica onde o texto vai sair, com a mesma letra
                textEdit?.let { e ->
                    val at = Offset(e.at.x * scale + offX, e.at.y * scale + offY)
                    val focus = remember(e) { FocusRequester() }
                    LaunchedEffect(e) { focus.requestFocus() }
                    val ink = e.index?.let { marks.getOrNull(it)?.color }?.let { Color(it) } ?: color
                    val maxWidth = with(density) { (viewW - at.x).coerceAtLeast(64.dp.toPx()).toDp() }
                    BasicTextField(
                        textValue,
                        { textValue = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ink,
                            fontSize = with(density) { textPx.toSp() },
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(Color.Black, blurRadius = strokePx),
                        ),
                        cursorBrush = SolidColor(ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitText() }),
                        modifier = Modifier
                            .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                            .widthIn(min = 64.dp, max = maxWidth)
                            .width(IntrinsicSize.Max)
                            .focusRequester(focus),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .background(Color(0x55000000), RoundedCornerShape(4.dp))
                                    .border(1.dp, ink.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 2.dp),
                            ) {
                                if (textValue.text.isEmpty()) {
                                    Text(
                                        stringResource(R.string.note_text_placeholder),
                                        style = TextStyle(color = ink.copy(alpha = 0.5f), fontSize = with(density) { textPx.toSp() }, fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            // ferramentas
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(R.drawable.ic_pixel_move, stringResource(R.string.note_tool_move), tool == NoteTool.MOVE, Modifier.weight(1f)) { commitText(); tool = NoteTool.MOVE }
                ToolButton(R.drawable.ic_pixel_arrow, stringResource(R.string.note_tool_arrow), tool == NoteTool.ARROW, Modifier.weight(1f)) { commitText(); tool = NoteTool.ARROW }
                ToolButton(R.drawable.ic_pixel_rect, stringResource(R.string.note_tool_rect), tool == NoteTool.RECTANGLE, Modifier.weight(1f)) { commitText(); tool = NoteTool.RECTANGLE }
                ToolButton(R.drawable.ic_pixel_circle, stringResource(R.string.note_tool_circle), tool == NoteTool.ELLIPSE, Modifier.weight(1f)) { commitText(); tool = NoteTool.ELLIPSE }
                ToolButton(R.drawable.ic_pixel_text, stringResource(R.string.note_tool_text), tool == NoteTool.TEXT, Modifier.weight(1f)) { commitText(); tool = NoteTool.TEXT }
            }
            // cores, desfazer e limpar
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MarkColors.forEach { c ->
                    val isCurrent = c == color
                    Spacer(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (isCurrent) 3.dp else 1.dp, if (isCurrent) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(role = Role.RadioButton) {
                                color = c
                                // com um desenho selecionado, a cor vale para ele
                                selected?.let { i -> change { marks[i] = marks[i].recolored(c.toArgb()) } }
                            }
                            .semantics { this.selected = isCurrent },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        undo.removeLastOrNull()?.let { before ->
                            marks.clear()
                            marks.addAll(before)
                            selected = null
                        }
                    },
                    enabled = undo.isNotEmpty(),
                ) {
                    Icon(painterResource(R.drawable.ic_pixel_undo), stringResource(R.string.note_undo), Modifier.size(22.dp))
                }
                // lixeira: apaga o desenho selecionado ou, sem seleção, todos
                val sel = selected
                IconButton(
                    onClick = {
                        if (sel != null) {
                            change { marks.removeAt(sel) }
                            selected = null
                        } else {
                            change { marks.clear() }
                        }
                    },
                    enabled = marks.isNotEmpty(),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_pixel_trash),
                        stringResource(if (sel != null) R.string.note_delete_selected else R.string.note_clear),
                        Modifier.size(22.dp),
                        tint = if (sel != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
            Text(
                stringResource(
                    when {
                        tool == NoteTool.MOVE && selected != null -> R.string.note_hint_selected
                        tool == NoteTool.MOVE -> R.string.note_hint_move
                        tool == NoteTool.TEXT && textEdit != null -> R.string.note_hint_typing
                        tool == NoteTool.TEXT -> R.string.note_hint_text
                        tool == NoteTool.ARROW -> R.string.note_hint_arrow
                        else -> R.string.note_hint_draw
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Texto sendo escrito na foto: em [at] (pixels da imagem); [index] = texto já existente sendo mudado. */
private data class TextEdit(val at: Offset, val index: Int?)

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
        // cinco ferramentas lado a lado: o nome diminui para caber inteiro ("Retângulo")
        BasicText(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = tint),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
