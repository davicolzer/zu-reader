package com.davicolzer.zureader.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.AppDialog
import com.davicolzer.zureader.data.HistoryItem
import com.davicolzer.zureader.data.SavedKind
import com.davicolzer.zureader.ui.components.Thumbnail
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.zu.ReadingMode
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    items: List<HistoryItem>,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
    /** Mosaico (grade de fotos) em vez de lista. */
    grid: Boolean = false,
    onGridChange: (Boolean) -> Unit = {},
    initialFilter: SavedKind? = null,
    /** Apaga o item (depois da confirmação). */
    onDelete: (Long) -> Unit = {},
) {
    // item esperando a confirmação para apagar
    var deleting by remember { mutableStateOf<HistoryItem?>(null) }
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
                title = { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (items.isNotEmpty()) {
                        // mostra o outro jeito de ver: em lista, o botão é "mosaico", e vice-versa
                        IconButton(onClick = { onGridChange(!grid) }) {
                            Icon(
                                painterResource(if (grid) R.drawable.ic_pixel_list else R.drawable.ic_pixel_grid),
                                stringResource(if (grid) R.string.history_show_list else R.string.history_show_grid),
                                Modifier.size(22.dp),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) { HistoryEmpty() }
            return@Scaffold
        }
        // null = tudo; o filtro só mostra os tipos que existem no histórico
        var filter by rememberSaveable { mutableStateOf(initialFilter) }
        val present = remember(items) { SavedKind.entries.filter { k -> items.any { it.kind == k } } }
        if (filter != null && filter !in present) filter = null
        val shown = remember(items, filter) { filter?.let { f -> items.filter { it.kind == f } } ?: items }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (present.size > 1) {
                // quebra em mais de uma linha em telas estreitas: todos os filtros sempre à vista
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KindChip(stringResource(R.string.history_filter_all), null, filter == null) { filter = null }
                    for (k in present) KindChip(stringResource(k.filterLabel), k, filter == k) { filter = k }
                }
            }
            if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(shown, key = { it.id }) { HistoryTile(it, onOpen, onDelete = { item -> deleting = item }) }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { HistoryRow(it, onOpen, onDelete = { item -> deleting = item }) }
                }
            }
        }
    }

    deleting?.let { item ->
        AppDialog(
            title = stringResource(R.string.history_delete_title),
            body = stringResource(R.string.history_delete_body, listTitle(item)),
            icon = R.drawable.ic_pixel_trash,
            iconTint = MaterialTheme.colorScheme.primary,
            confirmLabel = stringResource(R.string.result_delete),
            confirmIcon = R.drawable.ic_pixel_trash,
            onConfirm = { deleting = null; onDelete(item.id) },
            onDismiss = { deleting = null },
        )
    }
}

/** Botão de filtro por tipo (com o ícone do tipo; "Tudo" sem ícone). */
@Composable
private fun KindChip(label: String, kind: SavedKind?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = kind?.let { k -> { Icon(painterResource(k.icon), null, Modifier.size(16.dp), tint = k.color()) } },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

/** Nome, ícone e cor de cada tipo salvo. */
private val SavedKind.filterLabel: Int
    get() = when (this) {
        SavedKind.READING -> R.string.history_kind_reading
        SavedKind.WRITTEN -> R.string.history_kind_written
        SavedKind.QR -> R.string.history_kind_qr
        SavedKind.NOTE -> R.string.history_kind_note
        SavedKind.VIDEO -> R.string.history_kind_video
    }

private val SavedKind.badge: Int
    get() = when (this) {
        SavedKind.READING -> R.string.history_badge_reading
        SavedKind.WRITTEN -> R.string.history_badge_written
        SavedKind.QR -> R.string.history_badge_qr
        SavedKind.NOTE -> R.string.history_note_badge
        SavedKind.VIDEO -> R.string.history_badge_video
    }

private val SavedKind.icon: Int
    get() = when (this) {
        SavedKind.READING -> R.drawable.ic_fez_glyph
        SavedKind.WRITTEN -> R.drawable.ic_pixel_keyboard
        SavedKind.QR -> R.drawable.ic_pixel_qr
        SavedKind.NOTE -> R.drawable.ic_pixel_note
        SavedKind.VIDEO -> R.drawable.ic_pixel_video
    }

@Composable
private fun SavedKind.color(): Color = when (this) {
    SavedKind.READING -> ZuTheme.colors.teal
    SavedKind.WRITTEN -> MaterialTheme.colorScheme.tertiary
    SavedKind.QR -> MaterialTheme.colorScheme.onSurfaceVariant
    SavedKind.NOTE -> ZuTheme.colors.gold
    SavedKind.VIDEO -> MaterialTheme.colorScheme.primary
}

/** Quadrado do mosaico: a foto grande, o ícone do tipo num canto e o texto numa linha embaixo. */
@Composable
private fun HistoryTile(item: HistoryItem, onOpen: (Long) -> Unit, onDelete: (HistoryItem) -> Unit) {
    val description = listTitle(item)
    val deleteLabel = stringResource(R.string.history_delete_item)
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onOpen(item.id) }
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Thumbnail(item.image, size = maxWidth)
            Box(
                Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .padding(4.dp),
            ) {
                Icon(painterResource(item.kind.icon), stringResource(item.kind.badge), Modifier.size(14.dp), tint = item.kind.color())
            }
            // lixeira no canto de cima à direita
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(40.dp)
                    .clickable(role = Role.Button) { onDelete(item) }
                    .semantics { contentDescription = deleteLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .padding(4.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_pixel_trash), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(
            description,
            style = if (item.kind == SavedKind.QR) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** O texto que representa o item na lista e no mosaico: o nome, se tiver; senão, o conteúdo. */
@Composable
private fun listTitle(item: HistoryItem): String = item.name.takeIf { it.isNotBlank() }
    ?: contentText(item).takeIf { it.isNotBlank() }
    ?: stringResource(if (item.kind == SavedKind.VIDEO) R.string.video_default_name else R.string.note_default_name)

/** O conteúdo do item: a tradução, o texto do QR ou o nome da anotação. */
@Composable
private fun contentText(item: HistoryItem): String = when (item.kind) {
    SavedKind.QR -> item.text.removePrefix("QR: ")
    SavedKind.NOTE -> item.text.ifBlank { stringResource(R.string.note_default_name) }
    SavedKind.VIDEO -> item.description
    else -> ReadingMode.of(item.text.firstOrNull { !it.isWhitespace() } ?: 'A').display(item.text).replace('\n', ' ').ifBlank { "—" }
}

@Composable
fun HistoryRow(item: HistoryItem, onOpen: (Long) -> Unit, onDelete: ((HistoryItem) -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onOpen(item.id) }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(item.image, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // selo do tipo: o que gerou este item
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(item.kind.icon), null, Modifier.size(12.dp), tint = item.kind.color())
                Text(stringResource(item.kind.badge), style = MaterialTheme.typography.labelSmall, color = item.kind.color())
            }
            // com nome: o nome em destaque e o texto (tradução, QR) embaixo, menor
            if (item.name.isNotBlank() && !item.isNote) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (contentText(item).isNotBlank()) Text(
                    contentText(item),
                    style = if (item.kind == SavedKind.QR) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (item.kind == SavedKind.QR) {
                // QR: o conteúdo como está (pode ser link ou comando), sem a fonte de pixel
                Text(
                    item.text.removePrefix("QR: "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (item.kind == SavedKind.VIDEO) {
                // vídeo sem nome: "Vídeo" e a descrição, se houver
                Text(
                    stringResource(R.string.video_default_name),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                if (item.description.isNotBlank()) {
                    Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else if (item.isNote) {
                // foto anotada: o nome dado pelo usuário
                Text(
                    item.name.ifBlank { item.text }.ifBlank { stringResource(R.string.note_default_name) },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    ReadingMode.of(item.text.firstOrNull { !it.isWhitespace() } ?: 'A').display(item.text).replace('\n', ' ').ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        onDelete?.let { delete ->
            IconButton(onClick = { delete(item) }) {
                Icon(
                    painterResource(R.drawable.ic_pixel_trash),
                    stringResource(R.string.history_delete_item),
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun HistoryEmpty() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleSmall, color = ZuTheme.colors.gold)
        Text(stringResource(R.string.history_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
