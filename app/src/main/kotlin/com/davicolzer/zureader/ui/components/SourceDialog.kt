package com.davicolzer.zureader.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.davicolzer.zureader.R

/**
 * Padrão do app para pegar uma imagem: depois de escolher a ação (ler Zu, anotar, ler QR), este
 * modal pergunta de onde vem a imagem, com os botões do app: tirar foto ou escolher da galeria.
 * [extra] aparece entre o texto e os botões (ex.: o seletor Auto/Escrita/Números do "Ler Zu").
 */
@Composable
fun SourceDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    @DrawableRes icon: Int,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp),
                )
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                extra?.invoke()
                PixelPrimaryButton(stringResource(R.string.source_camera), R.drawable.ic_pixel_camera, onClick = onCamera)
                PixelOutlinedButton(stringResource(R.string.source_gallery), R.drawable.ic_pixel_gallery, onClick = onGallery)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
