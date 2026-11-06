package com.davicolzer.zureader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Cores do Zu Reader que não existem no ColorScheme do Material 3. */
@Immutable
data class ZuColors(
    val gold: Color = CubeGold,
    val teal: Color = ScanTeal,
    val fezDeep: Color = FezDeep,
    val ink: Color = Ink,
)

val LocalZuColors = staticCompositionLocalOf { ZuColors() }

private val ZuDarkScheme = darkColorScheme(
    primary = FezRed,
    onPrimary = Paper,
    primaryContainer = FezDeep,
    onPrimaryContainer = Paper,
    secondary = CubeGold,
    onSecondary = Night,
    tertiary = ScanTeal,
    onTertiary = Night,
    background = Night,
    onBackground = Paper,
    surface = Night,
    onSurface = Paper,
    surfaceVariant = Surface,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = Outline,
    outline = Outline,
    outlineVariant = Outline,
    inverseSurface = Paper,
    inverseOnSurface = Night,
    error = Color(0xFFFF8A80),
    onError = Night,
)

/**
 * Tema do app. Por enquanto só existe a versão escura do design;
 * o tema claro entra na Fase 5.
 */
@Composable
fun ZuReaderTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalZuColors provides ZuColors()) {
        MaterialTheme(
            colorScheme = ZuDarkScheme,
            typography = ZuTypography,
            content = content,
        )
    }
}

object ZuTheme {
    val colors: ZuColors
        @Composable get() = LocalZuColors.current
}
