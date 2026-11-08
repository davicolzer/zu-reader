package com.davicolzer.zureader.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

/** Duração do giro do cubo na troca de tela. */
const val CUBE_TURN_MS = 480

/**
 * Para usar com [cubeFace] num `AnimatedContent`: sem efeito próprio (o giro é feito por
 * [cubeFace]) e a tela que sai fica até o giro acabar.
 */
fun <S> AnimatedContentTransitionScope<S>.cubeTurnSpec(): ContentTransform =
    (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished).using(null)

/**
 * Cada tela é uma face de um cubo: na troca, o cubo gira e a nova tela vem da face ao lado, como a
 * troca de perspectiva do jogo. [forward] = indo para dentro do app (a nova vem da direita);
 * voltando, o giro é para o outro lado. A face que vira fica um pouco mais escura, como a sombra.
 */
@Composable
fun Modifier.cubeFace(scope: AnimatedVisibilityScope, forward: Boolean): Modifier {
    // posição da face: 0 = de frente; 1 = na lateral direita; −1 = na lateral esquerda
    val position by scope.transition.animateFloat(
        transitionSpec = { tween(CUBE_TURN_MS, easing = FastOutSlowInEasing) },
        label = "cubo",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> if (forward) 1f else -1f
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> if (forward) -1f else 1f
        }
    }
    return cubeAt { position }
}

/** O cubo parado numa posição (0 = de frente; 1 / −1 = nas laterais), lida a cada quadro. */
@Composable
fun Modifier.cubeAt(position: () -> Float): Modifier {
    val density = LocalDensity.current.density
    return graphicsLayer {
        val p = position()
        // a face gira pela aresta que encosta na outra face
        transformOrigin = TransformOrigin(if (p < 0f) 1f else 0f, 0.5f)
        rotationY = 90f * p
        translationX = size.width * p
        cameraDistance = 12f * density
    }.drawWithContent {
        drawContent()
        val p = abs(position())
        if (p > 0f) drawRect(Color.Black, alpha = 0.5f * p)
    }
}
