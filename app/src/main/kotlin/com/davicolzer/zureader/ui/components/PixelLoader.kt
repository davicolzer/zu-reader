package com.davicolzer.zureader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animação de carregamento: 4 pixels lado a lado, no vermelho dos botões, acendendo um de cada
 * vez da esquerda para a direita (os outros ficam apagados, mas à vista).
 */
@Composable
fun PixelLoader(modifier: Modifier = Modifier, pixel: Dp = 14.dp, gap: Dp = 8.dp) {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "carregando")
    // 0 → 4: qual pixel está aceso (com um rastro no anterior)
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
        label = "pixel",
    )
    Canvas(
        modifier
            .size(pixel * 4 + gap * 3, pixel)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val p = pixel.toPx()
        val g = gap.toPx()
        val lit = step.toInt().coerceIn(0, 3)
        for (i in 0 until 4) {
            val alpha = when (i) {
                lit -> 1f
                (lit + 3) % 4 -> 0.55f
                else -> 0.2f
            }
            drawRect(color.copy(alpha = alpha), Offset(i * (p + g), 0f), Size(p, p))
        }
    }
}
