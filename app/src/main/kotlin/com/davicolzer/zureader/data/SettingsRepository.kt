package com.davicolzer.zureader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.davicolzer.zureader.zu.ReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Histórico em mosaico (grade de fotos) em vez de lista. */
    val historyGrid: Boolean = false,
    /** Comandos: S = RT e Z = LT (em vez de S = LT e Z = RT), até a legenda do jogo confirmar. */
    val swapRotation: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Preferências do usuário, guardadas no aparelho. */
class SettingsRepository(private val context: Context) {
    private val directionKey = stringPreferencesKey("reading_direction")
    private val themeKey = stringPreferencesKey("theme")
    private val historyGridKey = booleanPreferencesKey("history_grid")
    private val swapRotationKey = booleanPreferencesKey("commands_swap_rotation")

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            direction = prefs[directionKey]?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() }
                ?: ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
            theme = prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            historyGrid = prefs[historyGridKey] ?: false,
            swapRotation = prefs[swapRotationKey] ?: false,
        )
    }

    suspend fun setDirection(direction: ReadingDirection) {
        context.dataStore.edit { it[directionKey] = direction.name }
    }

    suspend fun setTheme(theme: ThemeMode) {
        context.dataStore.edit { it[themeKey] = theme.name }
    }

    suspend fun setSwapRotation(swap: Boolean) {
        context.dataStore.edit { it[swapRotationKey] = swap }
    }

    suspend fun setHistoryGrid(grid: Boolean) {
        context.dataStore.edit { it[historyGridKey] = grid }
    }
}
