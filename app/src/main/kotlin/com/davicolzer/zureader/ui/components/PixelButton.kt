package com.davicolzer.zureader.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.ui.theme.ZuTheme

private val ButtonShape = RoundedCornerShape(14.dp)
private val ButtonHeight = 52.dp
private val Depth = 4.dp

/** Botão principal: vermelho com "degrau" de 4dp que afunda ao pressionar. */
@Composable
fun PixelPrimaryButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(ButtonHeight + Depth)
            .padding(top = if (pressed) Depth else 0.dp)
            .background(ZuTheme.colors.fezDeep, ButtonShape),
    ) {
        Button(
            onClick = onClick,
            interactionSource = interaction,
            shape = ButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            elevation = null,
        ) {
            ButtonContent(text, icon)
        }
    }
}

/** Botão secundário com contorno. */
@Composable
fun PixelOutlinedButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Para dois botões lado a lado: menos margem por dentro e texto numa linha só. */
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = if (compact) PaddingValues(horizontal = 8.dp) else ButtonDefaults.ContentPadding,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
    ) {
        ButtonContent(text, icon, compact)
    }
}

@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int, compact: Boolean = false) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = !compact)
}
