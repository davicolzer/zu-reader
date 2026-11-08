package com.davicolzer.zureader.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import com.davicolzer.zureader.vision.Viewport
import kotlin.math.abs

/**
 * Gestos de zoom de uma imagem num quadro: dois dedos ampliam e arrastam; ampliada, um dedo também
 * arrasta (sem zoom, o arrasto de um dedo fica para a rolagem da tela, a não ser com [consumeAll]).
 * [onTap] recebe um toque sem arrasto (posição na tela e quando foi).
 */
suspend fun PointerInputScope.detectZoomGestures(
    view: () -> Viewport,
    onView: (Viewport) -> Unit,
    consumeAll: Boolean = false,
    onTap: (position: Offset, uptimeMillis: Long) -> Unit = { _, _ -> },
) {
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
                onView(view().zoomedAt(c.x, c.y, event.calculateZoom()).panned(pan.x, pan.y))
                event.changes.forEach { it.consume() }
            } else {
                val change = pressed.first()
                val d = change.positionChange()
                moved += abs(d.x) + abs(d.y)
                if (view().zoom > 1f && !multi) {
                    onView(view().panned(d.x, d.y))
                    change.consume()
                }
                if (consumeAll) event.changes.forEach { it.consume() }
            }
        }
        if (!multi && moved < viewConfiguration.touchSlop) onTap(down.position, down.uptimeMillis)
    }
}
