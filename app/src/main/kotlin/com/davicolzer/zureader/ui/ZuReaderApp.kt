package com.davicolzer.zureader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davicolzer.zureader.BuildConfig
import com.davicolzer.zureader.R
import com.davicolzer.zureader.reader.ReaderUiState
import com.davicolzer.zureader.reader.ReaderViewModel
import com.davicolzer.zureader.ui.camera.CameraScreen
import com.davicolzer.zureader.ui.crop.CropScreen
import com.davicolzer.zureader.ui.history.HistoryScreen
import com.davicolzer.zureader.ui.home.HomeScreen
import com.davicolzer.zureader.ui.settings.SettingsScreen
import com.davicolzer.zureader.ui.theme.ZuReaderTheme
import com.davicolzer.zureader.ui.result.ResultScreen
import com.davicolzer.zureader.ui.write.WriteScreen
import com.davicolzer.zureader.ui.note.AnnotateScreen
import com.davicolzer.zureader.ui.note.NoteScreen
import com.davicolzer.zureader.ui.qr.QrScreen
import com.davicolzer.zureader.ui.components.ModeSelector
import com.davicolzer.zureader.ui.components.SourceDialog
import com.davicolzer.zureader.reader.CameraPurpose
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun ZuReaderApp(viewModel: ReaderViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val learned by viewModel.learned.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.read(uri)
    }
    val pickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // "Anotar foto": a imagem escolhida vai para o editor de anotações
    val notePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.annotate(uri)
    }
    var chooseNoteSource by remember { mutableStateOf(false) }
    // "Ler Zu": escolhe o modo (Auto/Escrita/Números) e de onde vem a imagem
    var chooseZuSource by remember { mutableStateOf(false) }
    if (chooseZuSource) {
        SourceDialog(
            title = R.string.zu_source_title,
            body = R.string.zu_source_body,
            icon = R.drawable.ic_fez_glyph,
            onCamera = { chooseZuSource = false; viewModel.openCamera() },
            onGallery = { chooseZuSource = false; pickImage() },
            onDismiss = { chooseZuSource = false },
            extra = { ModeSelector(settings.mode, viewModel::setMode) },
        )
    }
    // "Ler QR code": a imagem escolhida vai direto para a leitura do QR
    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.readQr(uri)
    }
    var chooseQrSource by remember { mutableStateOf(false) }
    if (chooseQrSource) {
        SourceDialog(
            title = R.string.qr_source_title,
            body = R.string.qr_source_body,
            icon = R.drawable.ic_pixel_qr,
            onCamera = { chooseQrSource = false; viewModel.openCameraForQr() },
            onGallery = { chooseQrSource = false; qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { chooseQrSource = false },
        )
    }
    if (chooseNoteSource) {
        SourceDialog(
            title = R.string.note_source_title,
            body = R.string.note_source_body,
            icon = R.drawable.ic_pixel_note,
            onCamera = { chooseNoteSource = false; viewModel.openCameraForNote() },
            onGallery = { chooseNoteSource = false; notePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { chooseNoteSource = false },
        )
    }

    when (val s = state) {
        ReaderUiState.Home, ReaderUiState.CouldNotOpen -> HomeScreen(
            onReadZu = { chooseZuSource = true },
            onOpenSettings = viewModel::openSettings,
            errorMessage = if (s == ReaderUiState.CouldNotOpen) stringResource(R.string.error_open_image) else null,
            onErrorShown = viewModel::goHome,
            recent = recent,
            onOpenSaved = viewModel::openSaved,
            onSeeAll = viewModel::openHistory,
            mode = settings.mode,
            onWrite = { viewModel.openWriter() },
            onAnnotate = { chooseNoteSource = true },
            onReadQr = { chooseQrSource = true },
        )
        is ReaderUiState.QrResult -> {
            val back = if (s.savedId != null) viewModel::openHistory else viewModel::goHome
            BackHandler(onBack = back)
            QrScreen(
                code = s.code,
                onReadAnother = { chooseQrSource = true },
                onBack = back,
                image = s.image,
                savedId = s.savedId,
                onCrop = viewModel::cropQr,
                onSave = viewModel::saveQr,
                onDelete = viewModel::deleteQr,
            )
        }
        is ReaderUiState.QrCrop -> {
            BackHandler(onBack = viewModel::leaveQrCrop)
            CropScreen(
                image = s.image,
                suggestion = com.davicolzer.zureader.vision.Quad.inset(s.image.width, s.image.height, 0.05f),
                onConfirm = { viewModel.confirmQrCrop(it) },
                onUseWhole = { viewModel.confirmQrCrop(null) },
                onRetake = viewModel::leaveQrCrop,
                confirmLabel = R.string.qr_crop_confirm,
            )
        }
        is ReaderUiState.Annotate -> {
            BackHandler(onBack = viewModel::leaveAnnotate)
            AnnotateScreen(image = s.image, title = s.title, onSave = viewModel::saveNote, onBack = viewModel::leaveAnnotate)
        }
        is ReaderUiState.Note -> {
            BackHandler(onBack = viewModel::openHistory)
            NoteScreen(image = s.image, title = s.title, onBack = viewModel::openHistory, onEdit = viewModel::editNote, onDelete = viewModel::deleteNote)
        }
        is ReaderUiState.Write -> {
            BackHandler(onBack = viewModel::goHome)
            WriteScreen(draft = s.draft, direction = settings.direction, onDone = viewModel::finishWriting, onBack = viewModel::goHome)
        }
        ReaderUiState.History -> {
            val all by viewModel.allHistory.collectAsStateWithLifecycle()
            BackHandler(onBack = viewModel::goHome)
            HistoryScreen(all, onOpen = viewModel::openSaved, onBack = viewModel::goHome)
        }
        is ReaderUiState.Camera -> {
            BackHandler(onBack = viewModel::goHome)
            // a câmera é sempre escura, independente do tema
            ZuReaderTheme(darkTheme = true) {
                CameraScreen(
                    onCaptured = viewModel::onCaptured,
                    // a galeria segue o propósito da câmera
                    onPickFromGallery = when (s.purpose) {
                        CameraPurpose.NOTE -> { { notePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
                        CameraPurpose.QR -> { { qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
                        CameraPurpose.READ -> pickImage
                    },
                    purpose = s.purpose,
                    onQrFound = viewModel::onQrFound,
                    onClose = viewModel::goHome,
                    direction = settings.direction,
                    learned = learned,
                    mode = settings.mode,
                    onMode = viewModel::setMode,
                )
            }
        }
        ReaderUiState.Settings -> {
            BackHandler(onBack = viewModel::goHome)
            SettingsScreen(
                settings = settings,
                versionName = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                onDirection = viewModel::setDirection,
                onTheme = viewModel::setTheme,
                learnedCount = learned.size,
                onForgetLearned = viewModel::forgetLearned,
                onBack = viewModel::goHome,
            )
        }
        is ReaderUiState.Crop -> {
            BackHandler(onBack = viewModel::leaveCrop)
            CropScreen(
                image = s.image,
                suggestion = s.suggestion,
                onConfirm = viewModel::confirmCrop,
                onUseWhole = viewModel::useWholePhoto,
                onRetake = viewModel::leaveCrop,
            )
        }
        ReaderUiState.Reading -> ReadingProgress()
        is ReaderUiState.Result -> {
            val back: () -> Unit = when {
                s.savedId != null -> viewModel::openHistory
                s.written != null -> { { viewModel.openWriter(s.written) } }
                else -> viewModel::goHome
            }
            BackHandler(onBack = back)
            ResultScreen(
                image = s.image,
                result = s.result,
                onBack = back,
                onPickAnother = pickImage,
                savedId = s.savedId,
                onCorrect = viewModel::correct,
                onSave = viewModel::save,
                onDelete = viewModel::deleteCurrent,
                onAdjustCrop = if (s.crop != null) viewModel::adjustCrop else null,
            )
        }
    }
}

@Composable
private fun ReadingProgress() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
            Text(stringResource(R.string.reading_progress), style = MaterialTheme.typography.titleSmall)
        }
    }
}
