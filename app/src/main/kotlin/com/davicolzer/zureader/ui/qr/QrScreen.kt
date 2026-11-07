package com.davicolzer.zureader.ui.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.QrCode
import kotlinx.coroutines.launch

/**
 * O que está no QR code: a imagem de onde ele foi lido (dá para recortar) e o texto inteiro,
 * grande e selecionável (pode ser um comando para digitar no jogo), com "Abrir link" quando é
 * endereço da web. "Salvar" guarda imagem e texto no histórico. [code] nulo = não achou QR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(
    code: QrCode?,
    onReadAnother: () -> Unit,
    onBack: () -> Unit,
    image: Bitmap? = null,
    savedId: Long? = null,
    onCrop: () -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.result_copied)
    val saved = stringResource(R.string.result_saved_message)
    var confirmDelete by remember { mutableStateOf(false) }
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
                title = { Text(stringResource(R.string.qr_title), style = MaterialTheme.typography.titleLarge) },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            image?.let { bitmap ->
                val shown = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    shown,
                    contentDescription = stringResource(R.string.qr_image_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZuTheme.colors.ink),
                )
            }
            // o conteúdo rola se for longo; os botões ficam sempre à vista embaixo
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (code == null) {
                    Text(stringResource(R.string.qr_not_found_title), style = MaterialTheme.typography.titleMedium, color = ZuTheme.colors.gold)
                    Text(stringResource(R.string.qr_not_found_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        stringResource(if (code.link != null) R.string.qr_kind_link else R.string.qr_kind_text),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    SelectionContainer {
                        Text(
                            code.text,
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            if (code != null) {
                code.link?.let { link ->
                    PixelOutlinedButton(stringResource(R.string.qr_open), R.drawable.ic_pixel_link, onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelOutlinedButton(stringResource(R.string.result_copy), R.drawable.ic_pixel_copy, onClick = {
                        copy(context, code.text)
                        scope.launch { snackbar.showSnackbar(copied) }
                    }, modifier = Modifier.weight(1f), compact = true)
                    PixelOutlinedButton(stringResource(R.string.result_share), R.drawable.ic_pixel_share, onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code.text)
                        context.startActivity(Intent.createChooser(send, null))
                    }, modifier = Modifier.weight(1f), compact = true)
                }
                if (savedId == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (image != null) {
                            PixelOutlinedButton(stringResource(R.string.qr_crop), R.drawable.ic_pixel_crop, onClick = onCrop, modifier = Modifier.weight(1f), compact = true)
                        }
                        PixelOutlinedButton(stringResource(R.string.qr_another_short), R.drawable.ic_pixel_qr, onClick = onReadAnother, modifier = Modifier.weight(1f), compact = true)
                    }
                    PixelPrimaryButton(stringResource(R.string.result_save), R.drawable.ic_pixel_save, onClick = {
                        onSave()
                        scope.launch { snackbar.showSnackbar(saved) }
                    })
                } else {
                    PixelOutlinedButton(stringResource(R.string.qr_read_another), R.drawable.ic_pixel_qr, onClick = onReadAnother)
                }
            } else {
                PixelPrimaryButton(stringResource(R.string.qr_read_another), R.drawable.ic_pixel_qr, onClick = onReadAnother)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.qr_delete_title)) },
            text = { Text(stringResource(R.string.qr_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.result_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("QR", text))
}
