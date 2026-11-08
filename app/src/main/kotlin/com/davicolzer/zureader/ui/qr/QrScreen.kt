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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.imePadding
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
import com.davicolzer.zureader.ui.components.ZoomableImage
import androidx.compose.foundation.layout.IntrinsicSize
import com.davicolzer.zureader.ui.components.ActionTile
import com.davicolzer.zureader.ui.components.ShareCard
import com.davicolzer.zureader.ui.components.DetailsFields
import com.davicolzer.zureader.ui.components.AppDialog
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
    /** Nome (de onde veio) e descrição, opcionais; [onDetails] grava as mudanças. */
    name: String = "",
    description: String = "",
    onDetails: (name: String, description: String) -> Unit = { _, _ -> },
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
                .imePadding(),
        ) {
            // tudo rola junto (imagem, conteúdo, nome e descrição, ações extras);
            // Copiar, Compartilhar e Salvar ficam fixos embaixo
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                image?.let { bitmap ->
                    val shown = remember(bitmap) { bitmap.asImageBitmap() }
                    // zoom dentro do quadro (o tamanho do quadro não muda)
                    ZoomableImage(
                        shown,
                        stringResource(R.string.qr_image_description),
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ZuTheme.colors.ink),
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZuTheme.colors.ink)
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
                    // nome (de onde veio) e descrição: o texto do QR continua separado
                    DetailsFields(name, description, onDetails)
                    if (savedId == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (image != null) {
                                PixelOutlinedButton(stringResource(R.string.qr_crop), R.drawable.ic_pixel_crop, onClick = onCrop, modifier = Modifier.weight(1f), compact = true)
                            }
                            PixelOutlinedButton(stringResource(R.string.qr_another_short), R.drawable.ic_pixel_qr, onClick = onReadAnother, modifier = Modifier.weight(1f), compact = true)
                        }
                    } else {
                        PixelOutlinedButton(stringResource(R.string.qr_read_another), R.drawable.ic_pixel_qr, onClick = onReadAnother)
                    }
                } else {
                    PixelPrimaryButton(stringResource(R.string.qr_read_another), R.drawable.ic_pixel_qr, onClick = onReadAnother)
                }
            }
            if (code != null) {
                // alturas iguais mesmo com fonte grande
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionTile(R.drawable.ic_pixel_copy, stringResource(R.string.result_copy), Modifier.weight(1f)) {
                        copy(context, code.text)
                        scope.launch { snackbar.showSnackbar(copied) }
                    }
                    ActionTile(R.drawable.ic_pixel_share, stringResource(R.string.result_share), Modifier.weight(1f)) {
                        // a imagem do QR com um rodapé (nome, conteúdo, descrição) e o texto na mensagem
                        ShareCard.share(context, image, name, code.text, description)
                    }
                    if (savedId == null) {
                        ActionTile(R.drawable.ic_pixel_save, stringResource(R.string.result_save), Modifier.weight(1f), primary = true) {
                            onSave()
                            scope.launch { snackbar.showSnackbar(saved) }
                        }
                    } else {
                        ActionTile(R.drawable.ic_pixel_check, stringResource(R.string.result_saved), Modifier.weight(1f), enabled = false) {}
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AppDialog(
            title = stringResource(R.string.qr_delete_title),
            body = stringResource(R.string.qr_delete_body),
            icon = R.drawable.ic_pixel_trash,
            iconTint = MaterialTheme.colorScheme.primary,
            confirmLabel = stringResource(R.string.result_delete),
            confirmIcon = R.drawable.ic_pixel_trash,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("QR", text))
}
