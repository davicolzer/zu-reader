package com.davicolzer.zureader.ui.video

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.davicolzer.zureader.R
import com.davicolzer.zureader.ui.components.ActionTile
import com.davicolzer.zureader.ui.components.AppDialog
import com.davicolzer.zureader.ui.components.DetailsFields
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.ShareCard
import com.davicolzer.zureader.ui.theme.ZuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Um vídeo (gravado agora, da galeria ou da coleção): o vídeo num quadro de tamanho fixo, com
 * tocar/pausar e a barra de tempo; "Ler Zu neste quadro" (pausa onde está e abre o recorte com
 * aquela imagem); nome e descrição; Compartilhar e Salvar fixos embaixo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    file: File,
    saved: Boolean,
    name: String,
    description: String,
    onDetails: (name: String, description: String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onReadFrame: (positionMs: Long) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.result_saved_message)
    var confirmDelete by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }

    // enquanto toca, a barra acompanha o vídeo
    LaunchedEffect(playing, player) {
        while (playing) {
            player?.let { position = it.currentPosition }
            if (player?.isPlaying == false) playing = false
            delay(200)
        }
    }
    DisposableEffect(file) { onDispose { player?.stopPlayback() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_pixel_back), stringResource(R.string.back), Modifier.size(22.dp))
                    }
                },
                title = { Text(stringResource(R.string.video_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_pixel_trash), stringResource(R.string.result_delete), Modifier.size(22.dp))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionTile(R.drawable.ic_pixel_share, stringResource(R.string.result_share), Modifier.weight(1f)) {
                    ShareCard.shareVideo(context, file, name, description)
                }
                if (!saved) {
                    ActionTile(R.drawable.ic_pixel_save, stringResource(R.string.result_save), Modifier.weight(1f), primary = true) {
                        onSave()
                        scope.launch { snackbar.showSnackbar(savedMessage) }
                    }
                } else {
                    ActionTile(R.drawable.ic_pixel_check, stringResource(R.string.result_saved), Modifier.weight(1f), enabled = false) {}
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // o vídeo num quadro de tamanho fixo (cabe inteiro, sem cortar)
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(file.path)
                        setOnPreparedListener { mp ->
                            duration = mp.duration
                            seekTo(1) // mostra o primeiro quadro
                        }
                        setOnCompletionListener { playing = false; position = duration }
                        player = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZuTheme.colors.ink),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    val p = player ?: return@IconButton
                    if (playing) p.pause() else p.start()
                    playing = !playing
                }) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pixel_pause else R.drawable.ic_pixel_play),
                        stringResource(if (playing) R.string.video_pause else R.string.video_play),
                        Modifier.size(24.dp),
                    )
                }
                Slider(
                    value = if (duration > 0) position / duration.toFloat() else 0f,
                    onValueChange = { f ->
                        position = (f * duration).toInt()
                        player?.seekTo(position)
                    },
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                Text("${time(position)} / ${time(duration)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PixelOutlinedButton(stringResource(R.string.video_read_frame), R.drawable.ic_fez_glyph, onClick = {
                player?.pause()
                playing = false
                onReadFrame((player?.currentPosition ?: position).toLong())
            })
            DetailsFields(name, description, onDetails)
        }
    }

    if (confirmDelete && onDelete != null) {
        AppDialog(
            title = stringResource(R.string.video_delete_title),
            body = stringResource(R.string.video_delete_body),
            icon = R.drawable.ic_pixel_trash,
            iconTint = MaterialTheme.colorScheme.primary,
            confirmLabel = stringResource(R.string.result_delete),
            confirmIcon = R.drawable.ic_pixel_trash,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun time(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
