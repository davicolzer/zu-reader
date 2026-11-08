package com.davicolzer.zureader

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davicolzer.zureader.data.Settings
import com.davicolzer.zureader.data.ThemeMode
import com.davicolzer.zureader.ui.ZuReaderApp
import com.davicolzer.zureader.ui.theme.ZuReaderTheme

class MainActivity : ComponentActivity() {
    // idioma escolhido no app (antes do Android 13; depois, o sistema aplica sozinho)
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsFlow = (application as ZuReaderApplication).settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = Settings())
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // ícones da barra de status acompanham o tema escolhido
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ZuReaderTheme(darkTheme = dark) {
                ZuReaderApp()
            }
        }
    }
}
