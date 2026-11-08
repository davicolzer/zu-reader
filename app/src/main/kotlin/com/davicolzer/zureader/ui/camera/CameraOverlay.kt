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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.vision.PhotoQuality
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.davicolzer.zureader.reader.CameraPurpose
import com.davicolzer.zureader.ui.theme.ZuTheme

private val Scrim = Color(0xCC0B0A18)

/**
 * Tudo o que fica por cima do visor: guia, qualidade da imagem e controles. Não há leitura ao
 * vivo: o texto é lido na foto, em resolução cheia. Em "Ler Zu", a cor da moldura e a frase
 * dizem se está bom (verde-água), médio (dourado) ou ruim (vermelho) para fotografar.
 * Separado do preview da câmera para poder ser testado com uma imagem qualquer atrás.
 */
@Composable
fun CameraOverlay(
    quality: PhotoQuality?,
    flashOn: Boolean,
    capturing: Boolean,
    onClose: () -> Unit,
    onToggleFlash: () -> Unit,
    onGallery: () -> Unit,
    onShutter: () -> Unit,
    horizontal: Boolean = false,
    /** Só na leitura de glifos aparece a ordem de leitura. */
    purpose: CameraPurpose = CameraPurpose.READ,
    /**
     * Quanto girar textos e ícones (0, 90 ou −90 graus) para ficarem em pé com o celular deitado:
     * a tela continua em retrato, só os elementos giram. Deitado, as frases vão para a lateral
     * (o "rodapé" de quem segura o celular assim).
     */
    turn: Int = 0,
) {
    val spin by animateFloatAsState(turn.toFloat(), tween(250), label = "giro")
    val landscape = turn != 0
    val reading = purpose == CameraPurpose.READ
    val gold = ZuTheme.colors.gold
    val frameColor = when (if (reading) quality else null) {
        PhotoQuality.GOOD -> ZuTheme.colors.teal
        PhotoQuality.FAIR -> gold
        PhotoQuality.POOR -> MaterialTheme.colorScheme.primary
        null -> gold
    }

    // fora de um Scaffold o texto não herda a cor do tema
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // cantos da guia (a cor mostra a qualidade)
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
                drawRect(frameColor, Offset(if (dx > 0) x else x - arm, if (dy > 0) y else y - t), Size(arm, t))
                drawRect(frameColor, Offset(if (dx > 0) x else x - t, if (dy > 0) y else y - arm), Size(t, arm))
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundControl(R.drawable.ic_pixel_close, stringResource(R.string.close), onClose, spin)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.rotate(spin).clip(RoundedCornerShape(50)).background(Scrim).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(
                            when (purpose) {
                                CameraPurpose.QR -> R.string.camera_qr_hint
                                CameraPurpose.NOTE -> R.string.camera_note_hint
                                CameraPurpose.READ -> R.string.home_read_zu
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                RoundControl(
                    if (flashOn) R.drawable.ic_pixel_flash else R.drawable.ic_pixel_flash_off,
                    stringResource(if (flashOn) R.string.camera_flash_off else R.string.camera_flash_on),
                    onToggleFlash,
                    spin,
                )
            }


            Spacer(Modifier.weight(1f))

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Scrim, 1f to Color(0xFF0B0A18)))
                    .padding(top = 28.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // em pé, as frases ficam aqui embaixo; deitado, vão para a lateral (abaixo)
                if (!landscape) Hints(reading, quality, frameColor, purpose, horizontal)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoundControl(R.drawable.ic_pixel_gallery, stringResource(R.string.home_pick_gallery), onGallery, spin)
                    Spacer(Modifier.weight(1f))
                    Shutter(capturing, onShutter)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
        // celular deitado: as frases giradas, ao longo da borda que fica embaixo para quem segura
        if (landscape) {
            Box(
                Modifier
                    .align(if (turn < 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp)
                    .rotateLayout(turn),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Scrim)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { Hints(reading, quality, frameColor, purpose, horizontal) }
            }
        }
    }
    }
}

/** As frases da câmera: qualidade (ou o que fazer) e, na leitura de Zu, a ordem de leitura. */
@Composable
private fun Hints(reading: Boolean, quality: PhotoQuality?, frameColor: Color, purpose: CameraPurpose, horizontal: Boolean) {
    if (reading && quality != null) {
        QualityLabel(quality, frameColor)
    } else {
        Text(
            stringResource(
                when (purpose) {
                    CameraPurpose.QR -> R.string.camera_qr_aim
                    CameraPurpose.NOTE -> R.string.camera_note_aim
                    CameraPurpose.READ -> R.string.camera_aim
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
    if (reading) {
        Text(
            stringResource(if (horizontal) R.string.camera_order_horizontal else R.string.camera_order),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Gira o conteúdo em 90° e troca largura por altura no layout (o [Modifier.rotate] comum só gira
 * o desenho, sem mudar o espaço ocupado): assim um texto girado ocupa uma faixa vertical.
 */
private fun Modifier.rotateLayout(degrees: Int): Modifier = layout { measurable, constraints ->
    if (degrees % 180 == 0) {
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { p.place(0, 0) }
    } else {
        val p = measurable.measure(
            Constraints(constraints.minHeight, constraints.maxHeight, constraints.minWidth, constraints.maxWidth),
        )
        layout(p.height, p.width) {
            p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = degrees.toFloat() }
        }
    }
}

/** A frase do nível de qualidade, com um ponto na cor da moldura. */
@Composable
private fun QualityLabel(quality: PhotoQuality, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            stringResource(
                when (quality) {
                    PhotoQuality.GOOD -> R.string.camera_quality_good
                    PhotoQuality.FAIR -> R.string.camera_quality_fair
                    PhotoQuality.POOR -> R.string.camera_quality_poor
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
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
private fun RoundControl(icon: Int, description: String, onClick: () -> Unit, spin: Float = 0f) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Scrim)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp).rotate(spin), tint = MaterialTheme.colorScheme.onSurface)
    }
}
