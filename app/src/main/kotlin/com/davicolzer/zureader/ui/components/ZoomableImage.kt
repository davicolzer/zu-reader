package com.davicolzer.zureader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.davicolzer.zureader.vision.Viewport
import kotlin.math.roundToInt

/**
 * Foto com zoom **dentro do quadro**: o tamanho do quadro vem do [modifier] e não muda ao
 * ampliar. Dois dedos ampliam (até 8×) e arrastam; ampliada, um dedo também arrasta (sem zoom,
 * o arrasto de um dedo fica para a rolagem da tela); dois toques voltam ao tamanho inteiro.
 */
@Composable
fun ZoomableImage(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier) {
    var stored by remember(bitmap) { mutableStateOf<Viewport?>(null) }
    BoxWithConstraints(modifier.clipToBounds().semantics { contentDescription?.let { this.contentDescription = it } }) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        if (viewW <= 0f || viewH <= 0f) return@BoxWithConstraints
        val current = stored
        val fitted = when {
            current == null -> Viewport.fit(viewW, viewH, bitmap.width, bitmap.height)
            current.viewWidth != viewW || current.viewHeight != viewH -> current.resized(viewW, viewH)
            else -> current
        }
        SideEffect { if (stored !== fitted) stored = fitted }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap, viewW, viewH) {
                    var lastTap = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = 0f
                        var multi = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                multi = true
                                val c = event.calculateCentroid()
                                val pan = event.calculatePan()
                                stored = (stored ?: fitted).zoomedAt(c.x, c.y, event.calculateZoom()).panned(pan.x, pan.y)
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = pressed.first()
                                val d = change.positionChange()
                                moved += kotlin.math.abs(d.x) + kotlin.math.abs(d.y)
                                if ((stored ?: fitted).zoom > 1f && !multi) {
                                    stored = (stored ?: fitted).panned(d.x, d.y)
                                    change.consume()
                                }
                            }
                        }
                        // dois toques rápidos: volta à foto inteira
                        if (!multi && moved < viewConfiguration.touchSlop) {
                            val now = down.uptimeMillis
                            if (now - lastTap < viewConfiguration.doubleTapTimeoutMillis) {
                                stored = Viewport.fit(viewW, viewH, bitmap.width, bitmap.height)
                                lastTap = 0L
                            } else {
                                lastTap = now
                            }
                        }
                    }
                },
        ) {
            val view = stored ?: fitted
            drawImage(
                bitmap,
                dstOffset = IntOffset(view.left.roundToInt(), view.top.roundToInt()),
                dstSize = IntSize((bitmap.width * view.scale).roundToInt(), (bitmap.height * view.scale).roundToInt()),
            )
        }
    }
}
