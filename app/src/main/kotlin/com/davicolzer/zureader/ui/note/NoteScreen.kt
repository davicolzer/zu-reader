package com.davicolzer.zureader.ui.note

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.ZoomableImage
import com.davicolzer.zureader.ui.components.ShareCard
import com.davicolzer.zureader.ui.components.DetailsFields
import com.davicolzer.zureader.ui.components.AppDialog
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.ui.theme.ZuTheme
import java.io.File

/**
 * A anotação pronta (depois do "OK" no editor, ou aberta do histórico): o nome em cima, a foto
 * com os desenhos e as ações "Editar", "Compartilhar" e "Salvar". [saved] = o que está na tela é o
 * que está no histórico (mudar o nome pede salvar de novo). [onDelete] nulo = ainda não salva.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    image: Bitmap,
    title: String,
    saved: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSave: (name: String, description: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** Descrição livre da anotação (o nome é [title]). */
    description: String = "",
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember(title) { mutableStateOf(title) }
    var about by remember(description) { mutableStateOf(description) }
    val upToDate = saved && name.trim() == title && about.trim() == description
    val bitmap = remember(image) { image.asImageBitmap() }
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
                title = { Text(stringResource(R.string.note_screen_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_pixel_trash), stringResource(R.string.result_delete), Modifier.size(22.dp))
                        }
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
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // nome e descrição, opcionais (nome em branco = "Anotação")
            DetailsFields(name, about, { n, d -> name = n; about = d })
            // zoom dentro do quadro (o tamanho do quadro não muda)
            ZoomableImage(
                bitmap,
                stringResource(R.string.note_image_description),
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelOutlinedButton(stringResource(R.string.note_edit), R.drawable.ic_pixel_pen, onClick = onEdit, modifier = Modifier.weight(1f), compact = true)
                PixelOutlinedButton(stringResource(R.string.note_share), R.drawable.ic_pixel_share, onClick = { ShareCard.share(context, image, name.ifBlank { null }, null, about) }, modifier = Modifier.weight(1f), compact = true)
            }
            if (upToDate) {
                PixelOutlinedButton(stringResource(R.string.note_saved), R.drawable.ic_pixel_check, onClick = {}, enabled = false)
            } else {
                PixelPrimaryButton(stringResource(R.string.note_save), R.drawable.ic_pixel_save, onClick = { onSave(name.trim(), about.trim()) })
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AppDialog(
            title = stringResource(R.string.note_delete_title),
            body = stringResource(R.string.note_delete_body),
            icon = R.drawable.ic_pixel_trash,
            iconTint = MaterialTheme.colorScheme.primary,
            confirmLabel = stringResource(R.string.result_delete),
            confirmIcon = R.drawable.ic_pixel_trash,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
