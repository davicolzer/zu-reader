package com.davicolzer.zureader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import com.davicolzer.zureader.ui.components.PixelLoader
import androidx.compose.animation.AnimatedContent
import com.davicolzer.zureader.ui.theme.Backdrop
import com.davicolzer.zureader.ui.components.cubeFace
import com.davicolzer.zureader.ui.components.cubeTurnSpec
import com.davicolzer.zureader.ui.video.VideoScreen
import androidx.compose.ui.platform.LocalContext
import com.davicolzer.zureader.data.AppLanguage
import android.app.Activity
import com.davicolzer.zureader.ui.camera.CameraScreen
import com.davicolzer.zureader.ui.crop.CropScreen
import com.davicolzer.zureader.ui.history.HistoryScreen
import com.davicolzer.zureader.ui.home.HomeScreen
import com.davicolzer.zureader.ui.settings.SettingsScreen
import com.davicolzer.zureader.ui.theme.ZuReaderTheme
import com.davicolzer.zureader.ui.result.ResultScreen
import com.davicolzer.zureader.ui.write.WriteScreen
import com.davicolzer.zureader.ui.note.AnnotateScreen
import com.davicolzer.zureader.ui.note.renderMarks
import com.davicolzer.zureader.ui.note.NoteScreen
import com.davicolzer.zureader.ui.qr.QrScreen
import com.davicolzer.zureader.ui.components.SourceDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.davicolzer.zureader.ui.commands.CommandsScreen
import com.davicolzer.zureader.ui.commands.CommandsWriteScreen
import com.davicolzer.zureader.ui.components.PixelOutlinedButton

@Composable
fun ZuReaderApp(viewModel: ReaderViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val learned by viewModel.learned.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
    // "Ler comandos": a imagem escolhida vai para o recorte e depois para a leitura das peças
    val commandsPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.readCommands(uri)
    }
    val pickCommands = { commandsPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    var chooseCommandsSource by remember { mutableStateOf(false) }
    if (chooseCommandsSource) {
        SourceDialog(
            title = R.string.commands_source_title,
            body = R.string.commands_source_body,
            icon = R.drawable.ic_pixel_commands,
            onCamera = { chooseCommandsSource = false; viewModel.openCameraForCommands() },
            onGallery = { chooseCommandsSource = false; pickCommands() },
            onDismiss = { chooseCommandsSource = false },
            // ou montar a sequência à mão, sem foto
            more = {
                PixelOutlinedButton(stringResource(R.string.commands_write_hand), R.drawable.ic_pixel_keyboard, onClick = {
                    chooseCommandsSource = false
                    viewModel.openCommandsWriter()
                })
            },
        )
    }
    // "Gravar vídeo": gravar agora ou escolher um vídeo da galeria
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickVideo(uri)
    }
    val pickVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    var chooseVideoSource by remember { mutableStateOf(false) }
    if (chooseVideoSource) {
        SourceDialog(
            title = R.string.video_source_title,
            body = R.string.video_source_body,
            icon = R.drawable.ic_pixel_video,
            onCamera = { chooseVideoSource = false; viewModel.openCameraForVideo() },
            onGallery = { chooseVideoSource = false; pickVideo() },
            onDismiss = { chooseVideoSource = false },
            cameraLabel = R.string.source_record,
            galleryLabel = R.string.source_video_gallery,
            cameraIcon = R.drawable.ic_pixel_video,
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

    // troca de tela = o cubo gira (como a troca de perspectiva do jogo); indo para dentro do app
    // gira para um lado, voltando gira para o outro
    val turn = remember { CubeTurn(state) }
    val forward = turn.update(state)
    AnimatedContent(
        targetState = state,
        modifier = Modifier.background(Backdrop),
        // a mesma tela atualizando (ex.: correção de uma letra, o aviso no início) não gira;
        // só a troca de tela
        contentKey = { screenKey(it) },
        transitionSpec = { cubeTurnSpec() },
        label = "telas",
    ) { target ->
      Box(Modifier.fillMaxSize().cubeFace(this, forward)) {
        when (val s = target) {
            ReaderUiState.Home, ReaderUiState.CouldNotOpen -> HomeScreen(
                onReadZu = { chooseZuSource = true },
                onOpenSettings = viewModel::openSettings,
                errorMessage = if (s == ReaderUiState.CouldNotOpen) stringResource(R.string.error_open_image) else null,
                onErrorShown = viewModel::goHome,
                recent = recent,
                onOpenSaved = viewModel::openSaved,
                onSeeAll = viewModel::openHistory,
                onWrite = { viewModel.openWriter() },
                onAnnotate = { chooseNoteSource = true },
                onReadQr = { chooseQrSource = true },
                onRecordVideo = { chooseVideoSource = true },
                onReadCommands = { chooseCommandsSource = true },
            )
            is ReaderUiState.Video -> {
                BackHandler(onBack = viewModel::leaveVideo)
                VideoScreen(
                    file = s.file,
                    saved = s.id != null,
                    name = s.name,
                    description = s.description,
                    onDetails = viewModel::setDetails,
                    onSave = viewModel::saveVideo,
                    onBack = viewModel::leaveVideo,
                    onReadFrame = viewModel::readVideoFrame,
                    onDelete = if (s.id != null) viewModel::deleteVideo else null,
                )
            }
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
                    name = s.name,
                    description = s.description,
                    onDetails = viewModel::setDetails,
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
                AnnotateScreen(
                    image = s.image,
                    marks = s.marks,
                    onDone = viewModel::finishAnnotating,
                    onCrop = viewModel::cropAnnotation,
                    onBack = viewModel::leaveAnnotate,
                )
            }
            is ReaderUiState.AnnotateCrop -> {
                BackHandler(onBack = viewModel::leaveAnnotationCrop)
                // mostra a foto com os desenhos; o recorte vale para a foto e para eles
                val shown = remember(s) { renderMarks(s.editing.image, s.editing.marks) }
                CropScreen(
                    image = shown,
                    suggestion = com.davicolzer.zureader.vision.Quad.inset(shown.width, shown.height, 0.05f),
                    onConfirm = { viewModel.confirmAnnotationCrop(it) },
                    onUseWhole = { viewModel.confirmAnnotationCrop(null) },
                    onRetake = viewModel::leaveAnnotationCrop,
                    confirmLabel = R.string.qr_crop_confirm,
                )
            }
            is ReaderUiState.Note -> {
                BackHandler(onBack = viewModel::leaveNote)
                NoteScreen(
                    image = s.image,
                    title = s.title,
                    saved = s.saved,
                    onBack = viewModel::leaveNote,
                    onEdit = viewModel::editNote,
                    onSave = viewModel::saveNote,
                    onDelete = if (s.id != null) viewModel::deleteNote else null,
                    description = s.description,
                )
            }
            is ReaderUiState.Write -> {
                BackHandler(onBack = viewModel::leaveWriter)
                WriteScreen(draft = s.draft, direction = settings.direction, onDone = viewModel::finishWriting, onBack = viewModel::leaveWriter)
            }
            ReaderUiState.History -> {
                val all by viewModel.allHistory.collectAsStateWithLifecycle()
                BackHandler(onBack = viewModel::goHome)
                HistoryScreen(all, onOpen = viewModel::openSaved, onBack = viewModel::goHome, grid = settings.historyGrid, onGridChange = viewModel::setHistoryGrid, onDelete = viewModel::deleteSaved)
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
                            CameraPurpose.VIDEO -> pickVideo
                            CameraPurpose.COMMANDS -> pickCommands
                        },
                        onVideoRecorded = viewModel::onVideoRecorded,
                        purpose = s.purpose,
                        onQrFound = viewModel::onQrFound,
                        onClose = viewModel::goHome,
                        direction = settings.direction,
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
                    language = remember { AppLanguage.current(context) },
                    // a tela é recriada no idioma novo e volta para as configurações
                    onLanguage = { language -> (context as? Activity)?.let { AppLanguage.set(it, language) } },
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
                    confirmLabel = if (s.commands) R.string.crop_read_commands else R.string.crop_confirm,
                )
            }
            is ReaderUiState.CommandsResult -> {
                val back = if (s.savedId != null) viewModel::openHistory else viewModel::goHome
                BackHandler(onBack = back)
                CommandsScreen(
                    reading = s.reading,
                    image = s.image,
                    onBack = back,
                    onReadAnother = { chooseCommandsSource = true },
                    savedId = s.savedId,
                    onAdjustCrop = if (s.crop != null && s.savedId == null) viewModel::adjustCommandsCrop else null,
                    onSave = viewModel::saveCommands,
                    onDelete = viewModel::deleteCommands,
                    name = s.name,
                    description = s.description,
                    onDetails = viewModel::setDetails,
                    onFixByHand = viewModel::fixCommands,
                )
            }
            is ReaderUiState.CommandsWrite -> {
                BackHandler(onBack = viewModel::leaveCommandsWriter)
                CommandsWriteScreen(initial = s.pieces, onDone = viewModel::finishCommandsWriting, onBack = viewModel::leaveCommandsWriter)
            }
            ReaderUiState.Reading -> ReadingProgress()
            is ReaderUiState.Result -> {
                val back: () -> Unit = when {
                    // aberto porque o texto já estava salvo: voltar é sair da leitura, não ir à coleção
                    s.fresh != null -> viewModel::goHome
                // leitura de um quadro de vídeo (ainda não salva): volta para o vídeo
                s.savedId == null && s.crop?.video != null -> viewModel::backToVideo
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
                    onRemoveGlyph = viewModel::removeGlyph,
                    onRestore = viewModel::restoreReading,
                    onSave = viewModel::save,
                    onDelete = viewModel::deleteCurrent,
                    onAdjustCrop = if (s.crop != null) viewModel::adjustCrop else null,
                    alreadySavedAt = s.savedAt.takeIf { s.fresh != null },
                    onReadAnyway = if (s.fresh != null) viewModel::readAnyway else null,
                    name = s.name,
                    description = s.description,
                    onDetails = viewModel::setDetails,
                    typedImage = s.typed?.image,
                    typedReading = s.typed?.reading,
                    // escrever à mão vale para leituras de foto (o resultado de "Escrever à mão" já é escrito)
                    onWriteByHand = if (s.written == null) viewModel::writeForPhoto else null,
                )
            }
        }
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
            PixelLoader()
            Text(stringResource(R.string.reading_progress), style = MaterialTheme.typography.titleSmall)
        }
    }
}


/** Qual tela é: o aviso "não deu para abrir" aparece na própria tela de início. */
private fun screenKey(s: ReaderUiState): Any = if (s == ReaderUiState.CouldNotOpen) ReaderUiState.Home::class else s::class

/** Lembra a última tela para saber se a troca vai para dentro do app (frente) ou volta. */
private class CubeTurn(private var last: ReaderUiState) {
    private var forward = true

    fun update(state: ReaderUiState): Boolean {
        if (screenKey(state) != screenKey(last)) forward = depth(state) >= depth(last)
        last = state
        return forward
    }

    /** Quão "dentro" do app a tela está: o início é 0; voltar = ir para uma tela mais rasa. */
    private fun depth(s: ReaderUiState): Int = when (s) {
        ReaderUiState.Home, ReaderUiState.CouldNotOpen -> 0
        ReaderUiState.History, ReaderUiState.Settings, is ReaderUiState.Camera -> 1
        is ReaderUiState.Crop, ReaderUiState.Reading, is ReaderUiState.Annotate, is ReaderUiState.Video -> 2
        is ReaderUiState.Write -> if (s.from != null) 4 else 2
        is ReaderUiState.CommandsWrite -> if (s.from != null) 4 else 2
        else -> 3
    }
}
