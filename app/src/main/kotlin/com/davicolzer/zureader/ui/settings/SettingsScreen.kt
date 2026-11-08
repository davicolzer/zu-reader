package com.davicolzer.zureader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import com.davicolzer.zureader.AppLanguage
import com.davicolzer.zureader.ui.components.AppDialog
import com.davicolzer.zureader.data.Settings
import com.davicolzer.zureader.data.ThemeMode
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.zu.ReadingDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    versionName: String,
    onDirection: (ReadingDirection) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    learnedCount: Int = 0,
    onForgetLearned: () -> Unit = {},
    /** Idioma atual do app e a troca (detectar, português ou inglês). */
    language: AppLanguage = AppLanguage.AUTO,
    onLanguage: (AppLanguage) -> Unit = {},
    /** Abre já com a escolha de idioma (prévias). */
    startChoosingLanguage: Boolean = false,
) {
    var confirmForget by remember { mutableStateOf(false) }
    var choosingLanguage by remember { mutableStateOf(startChoosingLanguage) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_pixel_back), stringResource(R.string.back), Modifier.size(22.dp))
                    }
                },
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(R.string.settings_reading)) {
                Option(
                    stringResource(R.string.settings_direction_vertical),
                    stringResource(R.string.settings_direction_vertical_body),
                    settings.direction == ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
                ) { onDirection(ReadingDirection.VERTICAL_RIGHT_TO_LEFT) }
                Option(
                    stringResource(R.string.settings_direction_horizontal),
                    stringResource(R.string.settings_direction_horizontal_body),
                    settings.direction == ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT,
                ) { onDirection(ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT) }
            }
            Section(stringResource(R.string.settings_learning)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        pluralStringResource(R.plurals.settings_learned_count, learnedCount, learnedCount),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.settings_learning_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PixelOutlinedButton(
                        stringResource(R.string.settings_forget),
                        R.drawable.ic_pixel_trash,
                        onClick = { confirmForget = true },
                        enabled = learnedCount > 0,
                    )
                }
            }
            Section(stringResource(R.string.settings_language)) {
                // botão de idioma: mostra o atual e abre a escolha
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { choosingLanguage = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_pixel_language), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.bodyLarge)
                        Text(languageName(language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.ic_pixel_chevron), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Section(stringResource(R.string.settings_appearance)) {
                Option(stringResource(R.string.settings_theme_system), null, settings.theme == ThemeMode.SYSTEM) { onTheme(ThemeMode.SYSTEM) }
                Option(stringResource(R.string.settings_theme_light), null, settings.theme == ThemeMode.LIGHT) { onTheme(ThemeMode.LIGHT) }
                Option(stringResource(R.string.settings_theme_dark), null, settings.theme == ThemeMode.DARK) { onTheme(ThemeMode.DARK) }
            }
            if (choosingLanguage) {
                LanguageDialog(
                    current = language,
                    onPick = { choosingLanguage = false; if (it != language) onLanguage(it) },
                    onDismiss = { choosingLanguage = false },
                )
            }
            if (confirmForget) {
                ForgetDialog(onConfirm = { confirmForget = false; onForgetLearned() }, onDismiss = { confirmForget = false })
            }
            Section(stringResource(R.string.settings_about)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.fan_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_offline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_credits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun languageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.AUTO -> R.string.language_auto
        AppLanguage.PORTUGUESE -> R.string.language_portuguese
        AppLanguage.ENGLISH -> R.string.language_english
    },
)

/** Escolha do idioma: detectar automaticamente ou um dos idiomas do app. Tocar aplica e fecha. */
@Composable
private fun LanguageDialog(current: AppLanguage, onPick: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(R.drawable.ic_pixel_language), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Column(Modifier.selectableGroup()) {
                    Option(stringResource(R.string.language_auto), stringResource(R.string.language_auto_body), current == AppLanguage.AUTO) { onPick(AppLanguage.AUTO) }
                    Option(stringResource(R.string.language_portuguese), null, current == AppLanguage.PORTUGUESE) { onPick(AppLanguage.PORTUGUESE) }
                    Option(stringResource(R.string.language_english), null, current == AppLanguage.ENGLISH) { onPick(AppLanguage.ENGLISH) }
                }
                PixelOutlinedButton(stringResource(R.string.cancel), R.drawable.ic_pixel_close, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ForgetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.settings_forget_title),
        body = stringResource(R.string.settings_forget_body),
        icon = R.drawable.ic_pixel_undo,
        iconTint = MaterialTheme.colorScheme.primary,
        confirmLabel = stringResource(R.string.settings_forget_confirm),
        confirmIcon = R.drawable.ic_pixel_trash,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .selectableGroup(),
        ) { content() }
    }
}

@Composable
private fun Option(title: String, body: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
