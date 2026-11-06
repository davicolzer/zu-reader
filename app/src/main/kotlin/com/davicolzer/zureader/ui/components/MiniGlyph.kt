package com.davicolzer.zureader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.zu.BitMatrix

/** Desenha um glifo Zu em blocos. */
@Composable
fun MiniGlyph(bits: BitMatrix, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val cell = this.size.width / bits.size
        for (r in 0 until bits.size) for (c in 0 until bits.size) {
            if (bits[r, c]) drawRect(color, Offset(c * cell, r * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
