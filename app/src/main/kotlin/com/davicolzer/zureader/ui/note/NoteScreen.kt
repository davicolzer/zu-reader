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
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember(title) { mutableStateOf(title) }
    val upToDate = saved && name.trim() == title
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
            // nome opcional; em branco, fica "Anotação"
            OutlinedTextField(
                name,
                { name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.note_name_label)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_pixel_note), null, Modifier.size(18.dp)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Image(
                bitmap,
                contentDescription = stringResource(R.string.note_image_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelOutlinedButton(stringResource(R.string.note_edit), R.drawable.ic_pixel_pen, onClick = onEdit, modifier = Modifier.weight(1f), compact = true)
                PixelOutlinedButton(stringResource(R.string.note_share), R.drawable.ic_pixel_share, onClick = { shareImage(context, image) }, modifier = Modifier.weight(1f), compact = true)
            }
            if (upToDate) {
                PixelOutlinedButton(stringResource(R.string.note_saved), R.drawable.ic_pixel_check, onClick = {}, enabled = false)
            } else {
                PixelPrimaryButton(stringResource(R.string.note_save), R.drawable.ic_pixel_save, onClick = { onSave(name.trim()) })
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

/** Compartilha a foto anotada (cópia temporária na pasta de cache, entregue por FileProvider). */
private fun shareImage(context: Context, image: Bitmap) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "anotacao.jpg")
    file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
