package com.davicolzer.zureader.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Tecla de ação dos teclados (espaço, apagar, mover o cursor, pronto): o ícone e, se [showLabel],
 * o texto; o texto sempre vai para o leitor de tela.
 */
@Composable
fun ActionKey(
    label: String,
    @DrawableRes icon: Int?,
    modifier: Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    showLabel: Boolean = icon == null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) container else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant
        if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        if (showLabel) Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}
