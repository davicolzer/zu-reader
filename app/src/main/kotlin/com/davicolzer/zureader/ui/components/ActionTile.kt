package com.davicolzer.zureader.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davicolzer.zureader.ui.theme.ZuTheme

/** Ação do rodapé (Copiar, Compartilhar, Salvar): ícone em cima e o nome embaixo, numa linha só. */
@Composable
fun ActionTile(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val content = when {
        !enabled -> ZuTheme.colors.teal
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        // encolhe para caber numa linha em vez de quebrar a palavra
        BasicText(
            label,
            style = MaterialTheme.typography.labelMedium.copy(color = content),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

