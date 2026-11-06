package com.davicolzer.zureader.data

import android.content.Context
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
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Preferências do usuário, guardadas no aparelho. */
class SettingsRepository(private val context: Context) {
    private val directionKey = stringPreferencesKey("reading_direction")
    private val themeKey = stringPreferencesKey("theme")

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            direction = prefs[directionKey]?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() }
                ?: ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
            theme = prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun setDirection(direction: ReadingDirection) {
        context.dataStore.edit { it[directionKey] = direction.name }
    }

    suspend fun setTheme(theme: ThemeMode) {
        context.dataStore.edit { it[themeKey] = theme.name }
    }
}
