package com.davicolzer.zureader.ui.write

import com.davicolzer.zureader.ui.components.ActionKey
import com.davicolzer.zureader.ui.components.cubeTurnSpec
import com.davicolzer.zureader.ui.components.cubeFace
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.input.pointer.pointerInput
import com.davicolzer.zureader.vision.Point
import com.davicolzer.zureader.vision.Viewport
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
import com.davicolzer.zureader.ui.components.AppTopBar
import com.davicolzer.zureader.ui.components.detectZoomGestures
import com.davicolzer.zureader.ui.components.CursorEdit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width

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
    /** Abre já com a mensagem em tela cheia (prévias). */
    startFullScreen: Boolean = false,
    /** Teclas por linha (4 ou 6; ver [KeyboardLayout]). */
    columns: Int = 6,
    /** Cursor e letra selecionada ao abrir (prévias); sem eles, o cursor fica no fim. */
    startCursor: Int? = null,
    startSelected: Int? = null,
) {
    val layout = KeyboardLayout.of(columns)
    // o texto com cursor (e letra selecionada), como num editor: digitar entra no cursor
    var edit by rememberSaveable(draft, saver = EditSaver) { mutableStateOf(CursorEdit(draft.toList(), startCursor ?: draft.length, startSelected)) }
    val text = edit.items.joinToString("")
    var byLetters by rememberSaveable { mutableStateOf(startWithLetters) }
    var fullScreen by rememberSaveable { mutableStateOf(startFullScreen) }
    // K/Q e U/V tocados pelo glifo: a letra vem do contexto do inglês e muda enquanto o usuário digita
    val message = ZuWriter.normalize(SharedLetters.resolve(text))
    // a mensagem em tela cheia é outra tela: entra girando o cubo, e fechar gira de volta
    AnimatedContent(fullScreen && message.isNotEmpty(), transitionSpec = { cubeTurnSpec() }, label = "mensagem") { full ->
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).cubeFace(this, forward = full)) {
        if (full) {
            FullMessage(message, direction, onClose = { fullScreen = false })
            return@Box
        }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(R.string.write_title, onBack)
        },
    ) { padding ->
      BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).navigationBarsPadding()) {
        // teclas do tamanho que cabe: grandes em tela alta; em tela baixa encolhem (até 36 dp)
        // para a mensagem em Zu continuar com espaço
        fun lines(n: Int) = (n + layout.columns - 1) / layout.columns
        val rows = if (byLetters) lines(26) + lines(12) else layout.letterRows().size + lines(ZuNumerals.glyphs.size)
        val keyHeight = ((maxHeight - MESSAGE_MIN - FIXED_PARTS) / rows - ROW_GAP).coerceIn(MIN_KEY, layout.keyHeight)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // mensagem: ocupa o espaço que o teclado deixa
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink)
                    // tocar na mensagem abre ela inteira em tela cheia
                    .clickable(enabled = message.isNotEmpty(), role = Role.Button, onClickLabel = stringResource(R.string.write_expand)) { fullScreen = true }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // o glifo no cursor (ou o selecionado) em dourado; o vão de espaço só no fim
                val atEnd = edit.selected == null && edit.cursor == text.length
                val highlight = glyphIndex(text, edit.selected ?: (edit.cursor - 1))
                ZuPreview(message, direction, Modifier.fillMaxWidth().weight(1f), pending = if (atEnd) pendingBreak(text) else null, highlight = highlight)
                // sem nada escrito, a dica usa a área toda (cabe em tela pequena)
                if (text.isNotEmpty()) {
                    CursorLine(
                        SharedLetters.resolve(text),
                        edit,
                        onSelect = { edit = edit.select(it) },
                        onEnd = { edit = edit.moveTo(text.length) },
                    )
                }
            }

            val space = {
                // não começa com espaço nem repete espaço (antes ou depois do cursor)
                val before = text.getOrNull((edit.selected ?: edit.cursor) - 1)
                if (before != null && before != ' ' && before != '\n') edit = edit.type(' ')
            }
            val newColumn = {
                // próxima coluna (ou linha) no cursor; tira os espaços logo antes dele
                if (message.isNotEmpty()) {
                    var e = edit
                    while (e.selected == null && e.cursor > 0 && e.items[e.cursor - 1] == ' ') e = e.delete()
                    edit = e.type('\n')
                }
            }
            val breakLabel = stringResource(if (direction == ReadingDirection.VERTICAL_RIGHT_TO_LEFT) R.string.write_new_column else R.string.write_new_line)
            // espaço e nova coluna em destaque, ao lado da escolha entre glifos e letras
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
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
                            // numa linha só, diminuindo se o idioma tiver um nome mais longo
                            BasicText(
                                stringResource(if (letters) R.string.write_by_letters else R.string.write_by_glyphs),
                                style = MaterialTheme.typography.labelLarge.copy(color = LocalContentColor.current),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                            )
                        }
                    }
                }
                ActionKey(
                    stringResource(R.string.write_space),
                    R.drawable.ic_pixel_space,
                    Modifier.width(52.dp),
                    container = ZuTheme.colors.gold.copy(alpha = 0.22f),
                    content = ZuTheme.colors.gold,
                    border = ZuTheme.colors.gold,
                    height = 40.dp,
                    onClick = space,
                )
                ActionKey(
                    breakLabel,
                    R.drawable.ic_pixel_newline,
                    Modifier.width(52.dp),
                    container = ZuTheme.colors.gold.copy(alpha = 0.22f),
                    content = ZuTheme.colors.gold,
                    border = ZuTheme.colors.gold,
                    height = 40.dp,
                    onClick = newColumn,
                )
            }

            val keyOf: @Composable (Key, Modifier) -> Unit = { key, modifier ->
                when (key) {
                    is Key.Glyph -> GlyphKey(key.glyph, modifier) { edit = edit.type(SharedLetters.typed(key.glyph)) }
                    is Key.Symbol -> LetterKey(key.symbol, modifier) { edit = edit.type(key.symbol) }
                }
            }
            if (!byLetters) {
                // pelo desenho (ver KeyboardLayout) e, embaixo, os desenhos de números
                KeyGrid(layout, keyHeight, layout.letterRows().map { row -> row.map { Key.Glyph(it) } }, ZuNumerals.glyphs.map { Key.Glyph(it) }.chunked(layout.columns), keyOf)
            } else {
                // um de cada, na ordem: A–Z e, separados, 0–10
                val numberSymbols = ('0'..'9').toList() + ReadingMode.TEN + ReadingMode.EQUALS
                KeyGrid(layout, keyHeight, ('A'..'Z').map { Key.Symbol(it) }.chunked(layout.columns), numberSymbols.map { Key.Symbol(it) }.chunked(layout.columns), keyOf)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // mover o cursor (o espaço e a nova coluna ficam ao lado da escolha de teclado)
                ActionKey(stringResource(R.string.cursor_left), R.drawable.ic_pixel_cursor_left, Modifier.weight(1f)) { edit = edit.left() }
                ActionKey(stringResource(R.string.cursor_right), R.drawable.ic_pixel_cursor_right, Modifier.weight(1f)) { edit = edit.right() }
                ActionKey(stringResource(R.string.write_delete), R.drawable.ic_pixel_backspace, Modifier.weight(1f)) {
                    edit = edit.delete()
                }
                ActionKey(stringResource(R.string.write_clear), R.drawable.ic_pixel_trash, Modifier.weight(1f)) { edit = edit.clear() }
                ActionKey(
                    stringResource(R.string.write_done),
                    R.drawable.ic_pixel_check,
                    Modifier.weight(1.9f),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    showLabel = true,
                    enabled = message.isNotEmpty(),
                ) { onDone(message) }
            }
        }
      }
    }
      }
    }
}

/**
 * Espaço ou nova coluna no fim do que foi digitado (ainda sem o próximo glifo): a prévia já mostra
 * o vão e o cursor onde o próximo glifo vai entrar. `null` se o texto não termina assim.
 */
private fun pendingBreak(text: String): Char? = when {
    text.endsWith('\n') -> '\n'
    text.endsWith(' ') -> ' '
    else -> null
}

/** O glifo (contando só letras e números, sem espaços) da posição [index] do texto; `null` se antes do começo. */
private fun glyphIndex(text: String, index: Int): Int? =
    if (index < 0 || index >= text.length) null else text.take(index + 1).count { !it.isWhitespace() } - 1

/** Guarda o texto, o cursor e a seleção ao girar a tela. */
private val EditSaver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.MutableState<CursorEdit<Char>>, Any>(
    save = { listOf(it.value.items.joinToString(""), it.value.cursor, it.value.selected ?: -1) },
    restore = { mutableStateOf(CursorEdit((it[0] as String).toList(), it[1] as Int, (it[2] as Int).takeIf { s -> s >= 0 })) },
)

/**
 * A linha do texto com o cursor: cada letra (em inglês) é tocável e fica selecionada para trocar ou
 * apagar; o cursor é uma barra dourada; tocar no fim põe o cursor lá (◀ ▶ ficam embaixo). A linha
 * rola até o cursor.
 */
@Composable
private fun CursorLine(
    text: String,
    edit: CursorEdit<Char>,
    onSelect: (Int) -> Unit,
    onEnd: () -> Unit,
) {
    val scroll = rememberScrollState()
    val gold = ZuTheme.colors.gold
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(scroll).semantics { contentDescription = text },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            text.forEachIndexed { i, ch ->
                if (edit.selected == null && edit.cursor == i) Caret(gold)
                val selected = edit.selected == i
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) gold else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (ch) {
                            ' ' -> "␣"
                            '\n' -> "|"
                            else -> ReadingMode.MIXED.symbolLabel(ch)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            selected -> MaterialTheme.colorScheme.background
                            ch.isWhitespace() -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
            }
            Box(Modifier.size(width = 24.dp, height = 28.dp).clickable(onClick = onEnd), contentAlignment = Alignment.CenterStart) {
                if (edit.selected == null && edit.cursor == text.length) Caret(gold)
            }
        }
    }
    // a linha acompanha o cursor (aproximado pela proporção do texto)
    LaunchedEffect(edit.cursor, edit.selected, text.length) {
        val at = (edit.selected ?: edit.cursor).toFloat() / maxOf(1, text.length)
        scroll.animateScrollTo((scroll.maxValue * at).toInt())
    }
}

@Composable
private fun Caret(color: Color) {
    Box(Modifier.padding(horizontal = 1.dp).size(width = 2.dp, height = 22.dp).background(color))
}

/** Uma tecla do teclado: um glifo, ou uma letra ou número. */
private sealed interface Key {
    data class Glyph(val glyph: ZuGlyph) : Key
    data class Symbol(val symbol: Char) : Key
}

/** Espaço mínimo da mensagem em Zu. */
private val MESSAGE_MIN = 170.dp

/** Tamanho dos glifos na mensagem: nunca menor (legível) nem maior que isto. */
private val PREVIEW_MIN_GLYPH = 26.dp
private val PREVIEW_MAX_GLYPH = 44.dp

/** Maior glifo na tela cheia. */
private val FULL_MAX_GLYPH = 72.dp

/** Altura do resto da tela fora as teclas: escolha Glifos/Letras, linha de ações, separação e espaços. */
private val FIXED_PARTS = 190.dp

/** Espaço entre linhas de teclas. */
private val ROW_GAP = 5.dp

/** Menor altura de tecla. */
private val MIN_KEY = 36.dp

/**
 * Como as letras ficam no teclado de glifos, sempre agrupadas pelo desenho:
 * - 4 colunas: uma linha por forma, com as 4 rotações lado a lado;
 * - 6 colunas: uma coluna por forma, e cada linha é uma rotação.
 */
private enum class KeyboardLayout(val columns: Int, val keyHeight: androidx.compose.ui.unit.Dp) {
    ROWS_BY_SHAPE(4, 40.dp),
    COLUMNS_BY_SHAPE(6, 46.dp);

    fun letterRows(): List<List<ZuGlyph>> = when (this) {
        ROWS_BY_SHAPE -> ZuAlphabet.shapes
        COLUMNS_BY_SHAPE -> (0..3).map { q -> ZuAlphabet.shapes.map { it[q] } }
    }

    companion object {
        fun of(columns: Int) = if (columns <= 4) ROWS_BY_SHAPE else COLUMNS_BY_SHAPE
    }
}

/** Espaço entre as letras e os números. */
private val SEPARATOR_HEIGHT = 12.dp

/** Grade de teclas: [top] e, depois de um respiro, [bottom] (as linhas incompletas são completadas com vazio). */
@Composable
private fun <T> KeyGrid(layout: KeyboardLayout, keyHeight: androidx.compose.ui.unit.Dp, top: List<List<T>>, bottom: List<List<T>>, key: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for ((index, row) in (top + bottom).withIndex()) {
            // separação entre letras e números: um respiro com uma linha fina no meio
            if (index == top.size && bottom.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(SEPARATOR_HEIGHT), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key(it, Modifier.weight(1f).height(keyHeight)) }
                repeat(layout.columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A mensagem inteira em tela cheia (uma tela própria): todas as colunas e o texto todo; fechar volta a escrever. */
@Composable
private fun FullMessage(message: String, direction: ReadingDirection, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.write_full_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_pixel_close), stringResource(R.string.write_full_close), Modifier.size(22.dp))
                }
            }
            ZoomableZu(
                message,
                direction,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink),
            )
            Text(
                stringResource(R.string.write_full_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                ReadingMode.MIXED.display(message),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
            )
        }
      }
}

/**
 * A mensagem inteira, que começa mostrando tudo; dois dedos ampliam, e ampliada um dedo arrasta.
 * (Mesma conta de [Viewport] da foto: a "imagem" é a mensagem em unidades de bloco × 10.)
 */
@Composable
private fun ZoomableZu(message: String, direction: ReadingDirection, modifier: Modifier) {
    val glyphs = remember(message, direction) { ZuWriter.layout(message, direction) }
    if (glyphs.isEmpty()) return
    val ink = MaterialTheme.colorScheme.onBackground
    val newestColor = ZuTheme.colors.gold
    val half = glyphs.first().size / 2
    val margin = half
    val minX = glyphs.minOf { it.x } - half - margin
    val minY = glyphs.minOf { it.y } - half - margin
    val width = ((glyphs.maxOf { it.x } + half + margin - minX) * UNIT).toInt()
    val height = ((glyphs.maxOf { it.y } + half + margin - minY) * UNIT).toInt()
    BoxWithConstraints(modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        var view by remember(message, viewW, viewH) { mutableStateOf(Viewport.fit(viewW, viewH, width, height)) }
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(message, viewW, viewH) {
                    detectZoomGestures({ view }, { view = it }, consumeAll = true)
                },
        ) {
            val cell = 2 * half * UNIT * view.scale / 5
            glyphs.forEachIndexed { i, g ->
                val p = view.toView(Point((g.x - half - minX) * UNIT, (g.y - half - minY) * UNIT))
                val color = if (i == glyphs.lastIndex) newestColor else ink
                for (r in 0 until 5) for (c in 0 until 5) if (g.bits[r, c]) {
                    drawRect(color, Offset(p.x + c * cell, p.y + r * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

/** Unidades de bloco → "pixels" da mensagem na tela cheia. */
private const val UNIT = 10f

/**
 * A mensagem desenhada em Zu, ajustada ao espaço; o último glifo em dourado.
 * Com [showAll], tudo cabe (tela cheia); sem, as colunas inteiras ficam legíveis e, se não couberem
 * todas, aparecem as mais recentes.
 */
@Composable
private fun ZuPreview(
    message: String,
    direction: ReadingDirection,
    modifier: Modifier,
    showAll: Boolean = false,
    /** Espaço ou nova coluna digitados por último: mostra o cursor onde o próximo glifo vai entrar. */
    pending: Char? = null,
    /** O glifo em dourado (o do cursor); sem ele, o último. */
    highlight: Int? = null,
) {
    val glyphs = remember(message, direction) { ZuWriter.layout(message, direction) }
    // onde o próximo glifo entraria: o mesmo texto com o espaço (ou a coluna nova) e mais uma letra
    val cursor = remember(message, direction, pending) {
        if (pending == null || message.isEmpty()) null
        else ZuWriter.layout(message + pending + "A", direction).lastOrNull()
    }
    val ink = MaterialTheme.colorScheme.onBackground
    val last = ZuTheme.colors.gold
    Box(modifier, contentAlignment = Alignment.Center) {
        if (glyphs.isEmpty()) {
            Text(
                stringResource(R.string.write_empty),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Canvas(Modifier.fillMaxSize().clipToBounds()) {
                val half = glyphs.first().size / 2
                val all = glyphs + listOfNotNull(cursor)
                val minX = all.minOf { it.x } - half
                val maxX = all.maxOf { it.x } + half
                val minY = all.minOf { it.y } - half
                val maxY = all.maxOf { it.y } + half
                // tamanho: cabe tudo se der; se não, a coluna (ou linha) inteira continua cabendo e
                // as colunas mais recentes ficam à vista; nunca maior que PREVIEW_MAX_GLYPH
                val fitAll = minOf(size.width / (maxX - minX), size.height / (maxY - minY))
                val fitLine = if (direction == ReadingDirection.VERTICAL_RIGHT_TO_LEFT) size.height / (maxY - minY) else size.width / (maxX - minX)
                val scale = if (showAll) {
                    minOf(fitAll, FULL_MAX_GLYPH.toPx() / (2 * half))
                } else {
                    minOf(maxOf(fitAll, minOf(fitLine, PREVIEW_MIN_GLYPH.toPx() / (2 * half))), PREVIEW_MAX_GLYPH.toPx() / (2 * half))
                }
                val contentW = (maxX - minX) * scale
                val contentH = (maxY - minY) * scale
                // Segue o cursor: se não couber, o último glifo fica junto da borda para onde a
                // mensagem cresce (colunas novas à esquerda; linhas novas embaixo), e o resto
                // mostra o que veio logo antes
                val newest = cursor ?: glyphs.last()
                val glyphPx = 2 * half * scale
                val edge = 2.dp.toPx()
                val vertical = direction == ReadingDirection.VERTICAL_RIGHT_TO_LEFT
                val offX = when {
                    contentW <= size.width -> (size.width - contentW) / 2
                    vertical -> (edge - (newest.x - half - minX) * scale).coerceIn(size.width - contentW, 0f)
                    else -> (size.width / 2 - (newest.x - minX) * scale).coerceIn(size.width - contentW, 0f)
                }
                val offY = when {
                    contentH <= size.height -> (size.height - contentH) / 2
                    !vertical -> (size.height - edge - (newest.y - minY) * scale - glyphPx / 2).coerceIn(size.height - contentH, 0f)
                    else -> (size.height / 2 - (newest.y - minY) * scale).coerceIn(size.height - contentH, 0f)
                }
                val cell = 2 * half * scale / 5
                glyphs.forEachIndexed { i, g ->
                    val left = offX + (g.x - half - minX) * scale
                    val top = offY + (g.y - half - minY) * scale
                    // fora o modo "tudo", só glifos inteiros: uma coluna cortada ao meio confunde
                    if (!showAll && (left < -0.5f || top < -0.5f || left + 2 * half * scale > size.width + 0.5f || top + 2 * half * scale > size.height + 0.5f)) return@forEachIndexed
                    val color = if (cursor == null && i == (highlight ?: glyphs.lastIndex)) last else ink
                    for (r in 0 until 5) for (c in 0 until 5) if (g.bits[r, c]) {
                        drawRect(color, Offset(left + c * cell, top + r * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
                // cursor: contorno dourado no lugar do próximo glifo (o vão do espaço fica visível)
                cursor?.let { c ->
                    val left = offX + (c.x - half - minX) * scale
                    val top = offY + (c.y - half - minY) * scale
                    val side = 2 * half * scale
                    drawRect(last, Offset(left, top), Size(side, side), style = Stroke(width = maxOf(2f, cell / 2.5f)))
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
            .clip(KeyShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, KeyShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) { content() }
}
