package com.davicolzer.zureader.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.davicolzer.zureader.R

/**
 * Modal padrão do app (confirmar, apagar, escrever um texto): ícone, título, texto, um conteúdo
 * opcional e os botões do app — a ação principal (vermelha) e "Cancelar" (contornado) — um
 * embaixo do outro, como no modal de escolher a imagem.
 */
@Composable
fun AppDialog(
    title: String,
    confirmLabel: String,
    @DrawableRes confirmIcon: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.tertiary,
    content: (@Composable () -> Unit)? = null,
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
                icon?.let { Icon(painterResource(it), contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp)) }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                content?.invoke()
                PixelPrimaryButton(confirmLabel, confirmIcon, onClick = onConfirm)
                PixelOutlinedButton(stringResource(R.string.cancel), R.drawable.ic_pixel_close, onClick = onDismiss)
            }
        }
    }
}
