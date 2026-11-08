package com.davicolzer.zureader.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.theme.ZuReaderTheme
import com.davicolzer.zureader.ui.theme.Backdrop
import com.davicolzer.zureader.ui.theme.Scrim
import com.davicolzer.zureader.ui.theme.Paper

/** O que aparece em tela cheia; recebe a função que fecha a tela cheia. */
typealias FullscreenContent = @Composable (close: () -> Unit) -> Unit

/** Abre algo (uma foto) em tela cheia, por cima da tela atual. Sem [FullscreenHost], não faz nada. */
val LocalFullscreen = staticCompositionLocalOf<(FullscreenContent) -> Unit> { {} }

/**
 * Tela cheia para fotos, para o app inteiro: abrir gira o cubo (como a troca de tela) e a foto
 * aparece em tela cheia, no escuro; fechar (X ou voltar) gira de volta. A tela de baixo continua
 * montada, então volta como estava (texto digitado, rolagem, zoom).
 */
@Composable
fun FullscreenHost(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf<FullscreenContent?>(null) }
    // o conteúdo continua na tela enquanto o cubo gira de volta
    var last by remember { mutableStateOf<FullscreenContent?>(null) }
    val open = shown != null
    val progress by updateTransition(open, label = "tela cheia").animateFloat(
        transitionSpec = { tween(CUBE_TURN_MS, easing = FastOutSlowInEasing) },
        label = "giro",
    ) { if (it) 1f else 0f }
    val close = { shown = null }
    CompositionLocalProvider(LocalFullscreen provides { c -> last = c; shown = c }) {
        Box(Modifier.fillMaxSize().background(Backdrop)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .cubeAt { -progress }
                    // com a tela cheia aberta, a tela de baixo some também para o leitor de tela
                    .then(if (open) Modifier.clearAndSetSemantics {} else Modifier),
            ) { content() }
            val viewer = last
            if (viewer != null && (open || progress > 0f)) {
                Box(Modifier.fillMaxSize().cubeAt { 1f - progress }) {
                    FullscreenFrame(close) { viewer(close) }
                }
            }
        }
    }
}

@Composable
private fun FullscreenFrame(onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onClose)
    // fotos ficam melhores no escuro, em qualquer tema (como na câmera)
    ZuReaderTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(Backdrop)) {
            content()
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Scrim)
                    .clickable(onClick = onClose)
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_pixel_close),
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Ícone de "ampliar" no canto de uma foto: mostra que tocar na foto abre a tela cheia. */
@Composable
fun ExpandHint(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(8.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Backdrop.copy(alpha = 0.7f))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_pixel_expand), null, Modifier.size(16.dp), tint = Paper)
    }
}
