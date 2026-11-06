package com.davicolzer.zureader.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.davicolzer.zureader.vision.Point
import com.davicolzer.zureader.vision.Quad
import kotlin.math.hypot

/**
 * Ajuste do recorte: o usuário arrasta os 4 cantos até as bordas do texto.
 * Um quadrilátero torto (foto de lado) é endireitado na leitura.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    image: Bitmap,
    suggestion: Quad,
    onConfirm: (Quad) -> Unit,
    onUseWhole: () -> Unit,
    onRetake: () -> Unit,
) {
    var corners by remember(suggestion) { mutableStateOf(suggestion.corners) }
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
                // ContentScale.Fit: imagem inteira visível, centralizada
                val scale = minOf(viewW / image.width, viewH / image.height)
                val offX = (viewW - image.width * scale) / 2
                val offY = (viewH - image.height * scale) / 2
                fun toView(p: Point) = Offset(p.x * scale + offX, p.y * scale + offY)
                val gold = ZuTheme.colors.gold
                val ink = MaterialTheme.colorScheme.background
                val touch = with(density) { 44.dp.toPx() }

                Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = description }
                        .pointerInput(scale, offX, offY) {
                            var active = -1
                            detectDragGestures(
                                onDragStart = { start ->
                                    fun distance(i: Int) = toView(corners[i]).let { hypot(it.x - start.x, it.y - start.y) }
                                    val nearest = corners.indices.minBy(::distance)
                                    active = if (distance(nearest) < touch * 1.5f) nearest else -1
                                },
                                onDragEnd = { active = -1 },
                                onDrag = { change, drag ->
                                    if (active >= 0) {
                                        change.consume()
                                        val p = corners[active]
                                        val moved = Point(
                                            (p.x + drag.x / scale).coerceIn(0f, image.width.toFloat()),
                                            (p.y + drag.y / scale).coerceIn(0f, image.height.toFloat()),
                                        )
                                        corners = corners.toMutableList().also { it[active] = moved }
                                    }
                                },
                            )
                        },
                ) {
                    val pts = corners.map(::toView)
                    val quad = Path().apply {
                        moveTo(pts[0].x, pts[0].y); for (p in pts.drop(1)) lineTo(p.x, p.y); close()
                    }
                    // escurece o que fica fora do recorte
                    val outside = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addPath(quad)
                    }
                    drawPath(outside, Color(0x99000000))
                    drawPath(quad, gold, style = Stroke(width = 2.5.dp.toPx()))
                    for (p in pts) {
                        drawCircle(gold, radius = 11.dp.toPx(), center = p)
                        drawCircle(ink, radius = 5.dp.toPx(), center = p)
                    }
                }
            }
            Text(
                stringResource(R.string.crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PixelPrimaryButton(stringResource(R.string.crop_confirm), R.drawable.ic_pixel_camera, onClick = {
                val (tl, tr, br, bl) = corners
                onConfirm(Quad(tl, tr, br, bl))
            })
            PixelOutlinedButton(stringResource(R.string.crop_whole), R.drawable.ic_pixel_gallery, onClick = onUseWhole)
        }
    }
}
