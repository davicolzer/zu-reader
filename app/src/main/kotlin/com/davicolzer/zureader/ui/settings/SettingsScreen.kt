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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.data.Settings
import com.davicolzer.zureader.data.ThemeMode
import com.davicolzer.zureader.zu.ReadingDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    versionName: String,
    onDirection: (ReadingDirection) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
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
            Section(stringResource(R.string.settings_appearance)) {
                Option(stringResource(R.string.settings_theme_system), null, settings.theme == ThemeMode.SYSTEM) { onTheme(ThemeMode.SYSTEM) }
                Option(stringResource(R.string.settings_theme_light), null, settings.theme == ThemeMode.LIGHT) { onTheme(ThemeMode.LIGHT) }
                Option(stringResource(R.string.settings_theme_dark), null, settings.theme == ThemeMode.DARK) { onTheme(ThemeMode.DARK) }
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
