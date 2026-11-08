package com.davicolzer.zureader.ui.result

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.davicolzer.zureader.vision.Viewport
import kotlin.math.roundToInt
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import com.davicolzer.zureader.ui.correct.CorrectionSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import android.net.Uri
import com.davicolzer.zureader.vision.QrCode
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.ActionTile
import com.davicolzer.zureader.ui.components.ShareCard
import com.davicolzer.zureader.ui.components.DetailsFields
import com.davicolzer.zureader.ui.components.AppDialog
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
    /** Tira o glifo [index] do texto (não era letra). */
    onRemoveGlyph: (index: Int) -> Unit = {},
    /** Volta a leitura para [previous] (desfazer a remoção). */
    onRestore: (previous: ImageReading) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    /** Volta para o recorte com os cantos usados; `null` se a leitura não veio de um recorte. */
    onAdjustCrop: (() -> Unit)? = null,
    /** Texto escrito à mão para esta foto, desenhado em Zu (imagem e leitura); `null` se não há. */
    typedImage: Bitmap? = null,
    typedReading: ImageReading? = null,
    /** Abre o teclado de glifos para escrever o texto da foto; `null` esconde a opção. */
    onWriteByHand: (() -> Unit)? = null,
    /** Não nulo = o texto lido já estava na coleção e este é o item salvo (quando foi salvo). */
    alreadySavedAt: Long? = null,
    /** "Ler mesmo assim": mostra a leitura que acabou de ser feita, no lugar do item salvo. */
    onReadAnyway: (() -> Unit)? = null,
    /** Nome (de onde veio) e descrição, opcionais; [onDetails] grava as mudanças. */
    name: String = "",
    description: String = "",
    onDetails: (name: String, description: String) -> Unit = { _, _ -> },
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.result_saved_message)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.result_copied)
    val removedMessage = stringResource(R.string.result_glyph_removed)
    val undoLabel = stringResource(R.string.note_undo)
    val reading = result.reading
    val mode = result.mode
    val bitmap = remember(image) { image.asImageBitmap() }
    val typed = typedReading != null && typedImage != null
    var noteClosed by remember(alreadySavedAt) { mutableStateOf(false) }
    // com texto escrito à mão, ele é o que vale: a foto aparece sem as letras lidas por cima
    val photoResult = remember(result, typed) {
        if (typed) result.copy(reading = result.reading.copy(glyphs = emptyList())) else result
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        // ações fixas no rodapé, sempre inteiras e acima da barra de gestos (o resto rola)
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                // alturas iguais mesmo com fonte grande
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile(R.drawable.ic_pixel_copy, stringResource(R.string.result_copy), Modifier.weight(1f)) {
                        copyText(context, shareable(result, typedReading))
                        scope.launch { snackbar.showSnackbar(copied) }
                    }
                    ActionTile(R.drawable.ic_pixel_share, stringResource(R.string.result_share), Modifier.weight(1f)) {
                        // a foto com um rodapé (nome, texto, descrição) e o mesmo texto na mensagem
                        ShareCard.share(context, image, name, shareable(result, typedReading), description)
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
        },
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
            if (!noteClosed) alreadySavedAt?.let { at -> AlreadySavedNote(at, onReadAnyway, onClose = { noteClosed = true }) }
            PhotoWithBoxes(bitmap, photoResult, selected = editing, onTapGlyph = { editing = it })
            if (reading.glyphs.isNotEmpty() && !typed) {
                Text(
                    stringResource(R.string.result_zoom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // ajustar o recorte lê de novo a foto: não oferece quando o texto foi escrito à mão
            if (onAdjustCrop != null && reading.glyphs.isNotEmpty() && !typed) {
                PixelOutlinedButton(stringResource(R.string.result_adjust_crop), R.drawable.ic_pixel_crop, onAdjustCrop)
            }

            result.qr?.let { qr ->
                QrCard(
                    qr,
                    onCopy = {
                        copyText(context, qr.text)
                        scope.launch { snackbar.showSnackbar(copied) }
                    },
                    onOpen = { link -> openLink(context, link) },
                )
            }

            if (typed) {
                TypedSection(typedImage!!, typedReading!!, onWriteByHand)
                DetailsFields(name, description, onDetails, showDescription = false)
                return@Column
            }

            if (reading.glyphs.isEmpty()) {
                // só o QR code: nada de glifos para mostrar
                if (result.qr == null) EmptyResult(onPickAnother, onAdjustCrop)
                onWriteByHand?.let { WriteByHandCard(it) }
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
                    .mapNotNull { g -> ZuAlphabet.twin(g.letter)?.let { listOf(g.letter, it).sorted() } }
                    .map { (a, b) -> stringResource(R.string.letters_pair, a.toString(), b.toString()) }
                    .distinct().sorted()
                if (pairs.isNotEmpty()) {
                    Text(
                        stringResource(R.string.result_twin_letters, pairs.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // nome (de onde veio), para reconhecer o item depois; a tradução já faz o papel de descrição
            DetailsFields(name, description, onDetails, showDescription = false)

            // separado das ações de salvar: uma saída para quando a foto não ajudou
            onWriteByHand?.let { WriteByHandCard(it) }

        }
    }

    editing?.let { index ->
        reading.glyphs.getOrNull(index)?.let { glyph ->
            CorrectionSheet(
                glyph = glyph,
                rotation = reading.rotation,
                mode = mode,
                onSelect = { letter -> onCorrect(index, letter); editing = null },
                onRemove = {
                    val before = result
                    onRemoveGlyph(index)
                    editing = null
                    scope.launch {
                        val r = snackbar.showSnackbar(removedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) onRestore(before)
                    }
                },
                onDismiss = { editing = null },
            )
        }
    }

    if (confirmDelete) {
        AppDialog(
            title = stringResource(R.string.result_delete_title),
            body = stringResource(R.string.result_delete_body),
            icon = R.drawable.ic_pixel_trash,
            iconTint = MaterialTheme.colorScheme.primary,
            confirmLabel = stringResource(R.string.result_delete),
            confirmIcon = R.drawable.ic_pixel_trash,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * Foto com uma caixa em cada glifo (turquesa = certo, dourado = com dúvida) e a letra lida
 * em cima de cada um. Tocar num glifo da foto abre a correção dele; o selecionado fica em destaque.
 */
/** Altura da foto sem zoom. */
private val PHOTO_MAX_HEIGHT = 320.dp

/** Parte da altura da tela que a foto ocupa quando ampliada. */
private const val ZOOMED_SHARE = 0.7f

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
    // A área usa a largura toda. Sem zoom, tem a altura da foto (até 320 dp); ao ampliar, cresce
    // para boa parte da tela, para o zoom ter espaço, mantendo no centro o ponto que se olhava.
    var stored by remember(result) { mutableStateOf<Viewport?>(null) }
    val zoomed = (stored?.zoom ?: 1f) > 1.01f
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val aspect = result.width / result.height.toFloat()
        val fullWidth = maxWidth
        val normalHeight = minOf(PHOTO_MAX_HEIGHT, fullWidth / aspect)
        val areaHeight by animateDpAsState(if (zoomed) maxOf(normalHeight, screenHeight * ZOOMED_SHARE) else normalHeight, label = "photo")
        Box(
            Modifier
                .fillMaxWidth()
                .height(areaHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(ZuTheme.colors.ink)
                .semantics { contentDescription = description },
        ) {
            val density = LocalDensity.current
            val viewW = with(density) { fullWidth.toPx() }
            val viewH = with(density) { areaHeight.toPx() }
            // zoom: dois dedos ampliam e arrastam; ampliada, um dedo também arrasta; toque corrige o glifo
            val current = stored
            val fitted = when {
                current == null -> Viewport.fit(viewW, viewH, result.width, result.height)
                current.viewWidth != viewW || current.viewHeight != viewH -> current.resized(viewW, viewH)
                else -> current
            }
            SideEffect { if (stored !== fitted) stored = fitted }
            val view = fitted
            val slop = LocalViewConfiguration.current.touchSlop
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(glyphs, viewW, viewH) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var moved = 0f
                            var multi = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    multi = true
                                    val c = event.calculateCentroid()
                                    val pan = event.calculatePan()
                                    stored = (stored ?: fitted).zoomedAt(c.x, c.y, event.calculateZoom()).panned(pan.x, pan.y)
                                    event.changes.forEach { it.consume() }
                                } else {
                                    val change = pressed.first()
                                    val d = change.positionChange()
                                    moved += kotlin.math.abs(d.x) + kotlin.math.abs(d.y)
                                    // sem zoom, o arrasto de um dedo fica para a rolagem da tela
                                    if ((stored ?: fitted).zoom > 1f && !multi) {
                                        stored = (stored ?: fitted).panned(d.x, d.y)
                                        change.consume()
                                    }
                                }
                            }
                            if (multi || moved > slop) return@awaitEachGesture
                            val p = (stored ?: fitted).toImage(down.position.x, down.position.y)
                            // o glifo cuja caixa contém o toque (ou o mais perto, até meio glifo de distância)
                            val nearest = glyphs.indices.minByOrNull { k ->
                                val g = glyphs[k].source
                                kotlin.math.hypot(g.x - p.x, g.y - p.y)
                            } ?: return@awaitEachGesture
                            val g = glyphs[nearest].source
                            if (kotlin.math.hypot(g.x - p.x, g.y - p.y) <= g.size) onTapGlyph(nearest)
                        }
                    },
            ) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(view.left.roundToInt(), view.top.roundToInt()),
                    dstSize = IntSize((result.width * view.scale).roundToInt(), (result.height * view.scale).roundToInt()),
                )
                val scale = view.scale
                val stroke = Stroke(width = 2.dp.toPx())
                val thick = Stroke(width = 4.dp.toPx())
                glyphs.forEachIndexed { i, g ->
                    val cx = g.source.x * scale + view.left
                    val cy = g.source.y * scale + view.top
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

/** Texto para copiar e compartilhar: o escrito à mão (ou o lido em Zu) e, se houver, o do QR code. */
private fun shareable(result: ImageReading, typed: ImageReading? = null): String {
    val text = typed ?: result
    return listOfNotNull(
        text.mode.display(text.reading.text).takeIf { it.isNotBlank() },
        result.qr?.text,
    ).joinToString("\n\n")
}

/**
 * Aviso discreto no topo: o texto lido já estava na coleção, então abriu o salvo. "Ler mesmo
 * assim" mostra a leitura feita agora (dá para salvar como outro item); o X fecha o aviso.
 */
@Composable
private fun AlreadySavedNote(savedAt: Long, onReadAnyway: (() -> Unit)?, onClose: () -> Unit) {
    val date = remember(savedAt) {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(savedAt))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_pixel_check), null, Modifier.size(14.dp), tint = ZuTheme.colors.teal)
        Text(
            stringResource(R.string.result_already_saved, date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        onReadAnyway?.let {
            TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.result_show_fresh), style = MaterialTheme.typography.labelMedium, color = ZuTheme.colors.teal)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ic_pixel_close), stringResource(R.string.close), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Botão para escrever o texto da foto à mão, olhando os glifos (o teclado abre com o que foi lido). */
@Composable
private fun WriteByHandCard(onWrite: () -> Unit) {
    PixelOutlinedButton(stringResource(R.string.result_write_button), R.drawable.ic_pixel_keyboard, onWrite)
}

/**
 * O texto escrito à mão para a foto: o texto em inglês e os glifos escritos (com a letra de cada
 * um), e o botão para mudar a escrita.
 */
@Composable
private fun TypedSection(image: Bitmap, typed: ImageReading, onWriteByHand: (() -> Unit)?) {
    val bitmap = remember(image) { image.asImageBitmap() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.result_typed_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        ReadText(typed.reading.text, typed.reading.glyphs, typed.mode)
        Text(
            stringResource(R.string.result_typed_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.result_typed_glyphs_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        PhotoWithBoxes(bitmap, typed, selected = null, onTapGlyph = {})
    }
    onWriteByHand?.let { PixelOutlinedButton(stringResource(R.string.result_typed_edit), R.drawable.ic_pixel_keyboard, it) }
}

/** Cartão do QR code: o texto inteiro, legível e selecionável; se for link, botão para abrir. */
@Composable
private fun QrCard(qr: QrCode, onCopy: () -> Unit, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.qr_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        // texto do jeito que veio (pode ser um comando para digitar no jogo): fonte monoespaçada,
        // grande e selecionável
        SelectionContainer {
            Text(
                qr.text,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        qr.link?.let { link ->
            PixelPrimaryButton(stringResource(R.string.qr_open), R.drawable.ic_pixel_link, onClick = { onOpen(link) })
        }
        PixelOutlinedButton(stringResource(R.string.qr_copy), R.drawable.ic_pixel_copy, onClick = onCopy)
    }
}

private fun openLink(context: Context, link: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Zu", text))
}
