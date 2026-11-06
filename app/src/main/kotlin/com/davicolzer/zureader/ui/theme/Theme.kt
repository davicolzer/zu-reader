package com.davicolzer.zureader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Cores do Zu Reader que não existem no ColorScheme do Material 3. */
@Immutable
data class ZuColors(
    val gold: Color,
    val teal: Color,
    val fezDeep: Color,
    val ink: Color,
)

private val DarkZuColors = ZuColors(gold = CubeGold, teal = ScanTeal, fezDeep = FezDeep, ink = Ink)
private val LightZuColors = ZuColors(gold = LightGold, teal = LightTeal, fezDeep = LightFezDeep, ink = LightInk)

val LocalZuColors = staticCompositionLocalOf { DarkZuColors }

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
    surfaceContainerHighest = Divider,
    outline = Outline,
    outlineVariant = Divider,
    inverseSurface = Paper,
    inverseOnSurface = Night,
    error = Color(0xFFFF8A80),
    onError = Night,
)

private val ZuLightScheme = lightColorScheme(
    primary = LightFezRed,
    onPrimary = Color.White,
    primaryContainer = LightFezDeep,
    onPrimaryContainer = Color.White,
    secondary = LightGold,
    onSecondary = Color.White,
    tertiary = LightTeal,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Night,
    surface = LightBackground,
    onSurface = Night,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightMuted,
    surfaceContainerLowest = LightInk,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightDivider,
    outline = LightOutline,
    outlineVariant = LightDivider,
    inverseSurface = Night,
    inverseOnSurface = Paper,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/** Tema do app. [darkTheme] segue o sistema, a menos que o usuário escolha nas configurações. */
@Composable
fun ZuReaderTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalZuColors provides if (darkTheme) DarkZuColors else LightZuColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) ZuDarkScheme else ZuLightScheme,
            typography = ZuTypography,
            content = content,
        )
    }
}

object ZuTheme {
    val colors: ZuColors
        @Composable get() = LocalZuColors.current
}
