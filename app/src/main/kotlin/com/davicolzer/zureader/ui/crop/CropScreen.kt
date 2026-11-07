package com.davicolzer.zureader.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.davicolzer.zureader.vision.Viewport
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.CropBox
import com.davicolzer.zureader.vision.Point
import com.davicolzer.zureader.vision.Quad

/**
 * Ajuste do recorte, sempre retangular (quinas a 90°): o usuário puxa os cantos ou as bordas,
 * arrasta o retângulo por dentro ou, tocando fora dele, desenha um retângulo novo. Com dois
 * dedos (pinça), amplia a foto e arrasta a parte ampliada, para recortar textos pequenos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    image: Bitmap,
    suggestion: Quad,
    onConfirm: (Quad) -> Unit,
    onUseWhole: () -> Unit,
    onRetake: () -> Unit,
    /** Texto do botão principal (ler o texto, ou só usar o recorte, no caso do QR). */
    confirmLabel: Int = R.string.crop_confirm,
) {
    var box by remember(suggestion, image) { mutableStateOf(CropBox.around(suggestion, image.width, image.height)) }
    val bitmap = remember(image) { image.asImageBitmap() }
    val description = stringResource(R.string.crop_description)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onRetake) {
                        Icon(painterResource(R.drawable.ic_pixel_back), stringResource(R.string.back), Modifier.size(22.dp))
                    }
                },
                title = { Text(stringResource(R.string.crop_title), style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val viewW = with(density) { maxWidth.toPx() }
                val viewH = with(density) { maxHeight.toPx() }
                // zoom: 1 = foto inteira visível; a pinça amplia, dois dedos arrastam a foto ampliada
                var view by remember(image, viewW, viewH) { mutableStateOf(Viewport.fit(viewW, viewH, image.width, image.height)) }
                fun toView(p: Point) = view.toView(p).let { Offset(it.x, it.y) }
                val gold = ZuTheme.colors.gold
                val ink = MaterialTheme.colorScheme.background
                val touch = with(density) { 28.dp.toPx() }
                val minSide = with(density) { 40.dp.toPx() }

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = description }
                        .pointerInput(image, viewW, viewH) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = view.toImage(down.position.x, down.position.y)
                                val handle = box.hit(start.x, start.y, touch / view.scale)
                                var finger = start
                                var pinching = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        // pinça: zoom em volta dos dedos e arrasto da foto; o recorte fica onde está
                                        pinching = true
                                        val centroid = event.calculateCentroid()
                                        val pan = event.calculatePan()
                                        view = view.zoomedAt(centroid.x, centroid.y, event.calculateZoom()).panned(pan.x, pan.y)
                                        event.changes.forEach { it.consume() }
                                    } else if (!pinching) {
                                        val change = pressed.first()
                                        val drag = change.positionChange()
                                        if (drag == Offset.Zero) continue
                                        change.consume()
                                        val dx = drag.x / view.scale
                                        val dy = drag.y / view.scale
                                        finger = Point(finger.x + dx, finger.y + dy)
                                        box = if (handle == CropBox.Handle.NEW) {
                                            CropBox.spanning(start, finger, image.width, image.height, minSide / view.scale)
                                        } else {
                                            box.drag(handle, dx, dy, image.width, image.height, minSide / view.scale)
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset(view.left.roundToInt(), view.top.roundToInt()),
                        dstSize = IntSize((image.width * view.scale).roundToInt(), (image.height * view.scale).roundToInt()),
                    )
                    val tl = toView(Point(box.left, box.top))
                    val br = toView(Point(box.right, box.bottom))
                    val rect = Rect(tl, br)
                    // escurece o que fica fora do recorte
                    val outside = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRect(rect)
                    }
                    drawPath(outside, Color(0x99000000))
                    // guias em terços, para alinhar o texto
                    val guide = gold.copy(alpha = 0.35f)
                    for (k in 1..2) {
                        val x = rect.left + rect.width * k / 3
                        val y = rect.top + rect.height * k / 3
                        drawLine(guide, Offset(x, rect.top), Offset(x, rect.bottom), 1.dp.toPx())
                        drawLine(guide, Offset(rect.left, y), Offset(rect.right, y), 1.dp.toPx())
                    }
                    drawRect(gold, rect.topLeft, rect.size, style = Stroke(width = 2.5.dp.toPx()))
                    // cantos em "L" (quina reta) e alças no meio das bordas
                    val arm = minOf(22.dp.toPx(), rect.width / 3, rect.height / 3)
                    val thick = 5.dp.toPx()
                    for ((corner, sx, sy) in listOf(
                        Triple(rect.topLeft, 1f, 1f), Triple(rect.topRight, -1f, 1f),
                        Triple(rect.bottomRight, -1f, -1f), Triple(rect.bottomLeft, 1f, -1f),
                    )) {
                        drawLine(gold, corner, corner + Offset(sx * arm, 0f), thick, cap = StrokeCap.Square)
                        drawLine(gold, corner, corner + Offset(0f, sy * arm), thick, cap = StrokeCap.Square)
                    }
                    for (mid in listOf(rect.centerLeft, rect.topCenter, rect.centerRight, rect.bottomCenter)) {
                        drawCircle(gold, radius = 7.dp.toPx(), center = mid)
                        drawCircle(ink, radius = 3.dp.toPx(), center = mid)
                    }
                }
            }
            Text(
                stringResource(R.string.crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PixelPrimaryButton(stringResource(confirmLabel), if (confirmLabel == R.string.crop_confirm) R.drawable.ic_pixel_camera else R.drawable.ic_pixel_check, onClick = { onConfirm(box.toQuad()) })
            PixelOutlinedButton(stringResource(R.string.crop_whole), R.drawable.ic_pixel_gallery, onClick = onUseWhole)
        }
    }
}
