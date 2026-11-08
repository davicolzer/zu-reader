package com.davicolzer.zureader.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.data.HistoryItem
import com.davicolzer.zureader.ui.history.HistoryEmpty
import com.davicolzer.zureader.ui.history.HistoryRow
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.ui.theme.ZuReaderTheme
import com.davicolzer.zureader.ui.theme.ZuTheme
import kotlinx.coroutines.launch

/**
 * Tela inicial: fotografar ou escolher uma imagem da galeria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onReadZu: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    errorMessage: String? = null,
    onErrorShown: () -> Unit = {},
    recent: List<HistoryItem> = emptyList(),
    onOpenSaved: (Long) -> Unit = {},
    onSeeAll: () -> Unit = {},
    onWrite: () -> Unit = {},
    onAnnotate: () -> Unit = {},
    onReadQr: () -> Unit = {},
    onRecordVideo: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.coming_soon)
    val notYet: () -> Unit = { scope.launch { snackbar.showSnackbar(comingSoon) } }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_fez_hat),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.app_title), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings ?: notYet,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_pixel_settings), contentDescription = stringResource(R.string.settings), modifier = Modifier.size(20.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard()
            // ações: o que fazer; de onde vem a imagem (câmera ou galeria) é perguntado depois
            PixelPrimaryButton(
                text = stringResource(R.string.home_read_zu),
                icon = R.drawable.ic_fez_glyph,
                onClick = onReadZu,
            )
            // anotar e QR lado a lado
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelOutlinedButton(
                    text = stringResource(R.string.home_annotate),
                    icon = R.drawable.ic_pixel_note,
                    onClick = onAnnotate,
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
                PixelOutlinedButton(
                    text = stringResource(R.string.home_read_qr),
                    icon = R.drawable.ic_pixel_qr,
                    onClick = onReadQr,
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
            }
            // vídeo e, por último, escrever à mão, lado a lado
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelOutlinedButton(
                    text = stringResource(R.string.home_record_video),
                    icon = R.drawable.ic_pixel_video,
                    onClick = onRecordVideo,
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
                PixelOutlinedButton(
                    text = stringResource(R.string.home_write),
                    icon = R.drawable.ic_pixel_keyboard,
                    onClick = onWrite,
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_recent),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (recent.isNotEmpty()) {
                    TextButton(onClick = onSeeAll) {
                        Text(stringResource(R.string.history_see_all), color = ZuTheme.colors.gold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (recent.isEmpty()) HistoryEmpty() else recent.forEach { HistoryRow(it, onOpenSaved) }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.fan_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HeroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ZuTheme.colors.ink),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_fez_hat), contentDescription = null, modifier = Modifier.size(56.dp))
        }
        Text(
            stringResource(R.string.home_hero_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.home_hero_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1B3A, widthDp = 360, heightDp = 760)
@Composable
private fun HomeScreenPreview() {
    ZuReaderTheme { HomeScreen() }
}
