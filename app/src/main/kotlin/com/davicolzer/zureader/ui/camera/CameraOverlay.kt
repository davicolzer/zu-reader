package com.davicolzer.zureader.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.vision.ModeChoice
import com.davicolzer.zureader.R
import com.davicolzer.zureader.reader.CameraPurpose
import com.davicolzer.zureader.reader.LiveFrame
import com.davicolzer.zureader.ui.components.ModeSelector
import com.davicolzer.zureader.ui.theme.ZuTheme
import com.davicolzer.zureader.vision.FillCenter
import com.davicolzer.zureader.zu.ReadingMode

private val Scrim = Color(0xCC0B0A18)

/**
 * Tudo o que fica por cima do visor: guia, caixas dos glifos, contador e controles.
 * Separado do preview da câmera para poder ser testado com uma imagem qualquer atrás.
 */
@Composable
fun CameraOverlay(
    frame: LiveFrame?,
    flashOn: Boolean,
    capturing: Boolean,
    onClose: () -> Unit,
    onToggleFlash: () -> Unit,
    onGallery: () -> Unit,
    onShutter: () -> Unit,
    horizontal: Boolean = false,
    mode: ModeChoice = ModeChoice.AUTO,
    onMode: (ModeChoice) -> Unit = {},
    /** Só na leitura de glifos aparecem o contador, o seletor de modo e o texto ao vivo. */
    purpose: CameraPurpose = CameraPurpose.READ,
) {
    val reading = purpose == CameraPurpose.READ
    val teal = ZuTheme.colors.teal
    val gold = ZuTheme.colors.gold
    val count = frame?.glyphs?.size ?: 0

    // fora de um Scaffold o texto não herda a cor do tema
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize()) {
        // caixas dos glifos, alinhadas ao preview (FILL_CENTER)
        Canvas(Modifier.fillMaxSize()) {
            val f = frame ?: return@Canvas
            val map = FillCenter.of(f.width, f.height, size.width, size.height)
            val stroke = Stroke(width = 2.dp.toPx())
            for (g in f.glyphs) {
                val w = g.width * map.scale + 6
                val h = g.height * map.scale + 6
                drawRect(
                    color = if (g.lowConfidence) gold else teal,
                    topLeft = Offset(map.mapX(g.x) - w / 2, map.mapY(g.y) - h / 2),
                    size = Size(w, h),
                    style = stroke,
                )
            }
            // cantos dourados da guia
            val gw = size.width * 0.8f
            val gh = size.height * 0.5f
            val left = (size.width - gw) / 2
            val top = size.height * 0.2f
            val arm = 26.dp.toPx()
            val t = 4.dp.toPx()
            for ((x, y, dx, dy) in listOf(
                listOf(left, top, 1f, 1f), listOf(left + gw, top, -1f, 1f),
                listOf(left, top + gh, 1f, -1f), listOf(left + gw, top + gh, -1f, -1f),
            )) {
                drawRect(gold, Offset(if (dx > 0) x else x - arm, if (dy > 0) y else y - t), Size(arm, t))
                drawRect(gold, Offset(if (dx > 0) x else x - t, if (dy > 0) y else y - arm), Size(t, arm))
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundControl(R.drawable.ic_pixel_close, stringResource(R.string.close), onClose)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Scrim).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (reading) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (count > 0) teal else MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(pluralStringResource(R.plurals.camera_glyph_count, count, count), style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text(
                            stringResource(if (purpose == CameraPurpose.QR) R.string.camera_qr_hint else R.string.camera_note_hint),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                RoundControl(
                    if (flashOn) R.drawable.ic_pixel_flash else R.drawable.ic_pixel_flash_off,
                    stringResource(if (flashOn) R.string.camera_flash_off else R.string.camera_flash_on),
                    onToggleFlash,
                )
            }

            if (reading) ModeSelector(mode, onMode, Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.weight(1f))

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Scrim, 1f to Color(0xFF0B0A18)))
                    .padding(top = 28.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val text = (frame?.mode ?: ReadingMode.TEXT).display(frame?.text.orEmpty()).replace('\n', ' ')
                if (text.isNotBlank()) {
                    Text(
                        text,
                        style = MaterialTheme.typography.titleLarge,
                        color = teal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Scrim)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Text(
                    stringResource(
                        when {
                            purpose == CameraPurpose.QR -> R.string.camera_qr_aim
                            purpose == CameraPurpose.NOTE -> R.string.camera_note_aim
                            count > 0 -> R.string.camera_hold
                            else -> R.string.camera_aim
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (reading) {
                    Text(
                        stringResource(if (horizontal) R.string.camera_order_horizontal else R.string.camera_order),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoundControl(R.drawable.ic_pixel_gallery, stringResource(R.string.home_pick_gallery), onGallery)
                    Spacer(Modifier.weight(1f))
                    Shutter(capturing, onShutter)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun Shutter(capturing: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.camera_shutter)
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(enabled = !capturing, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button }
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (capturing) ZuTheme.colors.fezDeep else MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (capturing) CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
    }
}

@Composable
private fun RoundControl(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Scrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
