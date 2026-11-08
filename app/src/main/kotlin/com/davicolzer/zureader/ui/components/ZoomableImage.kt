package com.davicolzer.zureader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.davicolzer.zureader.R
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.davicolzer.zureader.vision.Viewport
import kotlin.math.roundToInt
import com.davicolzer.zureader.ui.components.detectZoomGestures

/**
 * Foto com zoom **dentro do quadro**: o tamanho do quadro vem do [modifier] e não muda ao
 * ampliar. Dois dedos ampliam (até 8×) e arrastam; ampliada, um dedo também arrasta (sem zoom,
 * o arrasto de um dedo fica para a rolagem da tela).
 *
 * [fullscreen]: um toque abre a foto inteira em tela cheia, e um ícone no canto mostra
 * isso. Na tela cheia ([fullscreen] = `false`), dois toques voltam ao tamanho inteiro.
 */
@Composable
fun ZoomableImage(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, fullscreen: Boolean = true) {
    var stored by remember(bitmap) { mutableStateOf<Viewport?>(null) }
    val openFullscreen = LocalFullscreen.current
    val open = {
        openFullscreen { _ -> ZoomableImage(bitmap, contentDescription, Modifier.fillMaxSize(), fullscreen = false) }
    }
    val openLabel = stringResource(R.string.photo_fullscreen)
    BoxWithConstraints(
        modifier.clipToBounds().semantics {
            contentDescription?.let { this.contentDescription = it }
            if (fullscreen) onClick(openLabel) { open(); true }
        },
    ) {
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
                    detectZoomGestures({ stored ?: fitted }, { stored = it }) { _, time ->
                        when {
                            // um toque: tela cheia
                            fullscreen -> open()
                            // na tela cheia, dois toques rápidos voltam à foto inteira
                            time - lastTap < viewConfiguration.doubleTapTimeoutMillis -> {
                                stored = Viewport.fit(viewW, viewH, bitmap.width, bitmap.height)
                                lastTap = 0L
                            }
                            else -> lastTap = time
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
        if (fullscreen) ExpandHint(Modifier.align(Alignment.TopEnd))
    }
}
