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

    when (val s = state) {
        ReaderUiState.Home, ReaderUiState.CouldNotOpen -> HomeScreen(
            onTakePhoto = viewModel::openCamera,
            onOpenSettings = viewModel::openSettings,
            onPickFromGallery = pickImage,
            errorMessage = if (s == ReaderUiState.CouldNotOpen) stringResource(R.string.error_open_image) else null,
            onErrorShown = viewModel::goHome,
            recent = recent,
            onOpenSaved = viewModel::openSaved,
            onSeeAll = viewModel::openHistory,
        )
        ReaderUiState.History -> {
            val all by viewModel.allHistory.collectAsStateWithLifecycle()
            BackHandler(onBack = viewModel::goHome)
            HistoryScreen(all, onOpen = viewModel::openSaved, onBack = viewModel::goHome)
        }
        ReaderUiState.Camera -> {
            BackHandler(onBack = viewModel::goHome)
            // a câmera é sempre escura, independente do tema
            ZuReaderTheme(darkTheme = true) {
                CameraScreen(
                    onCaptured = viewModel::onCaptured,
                    onPickFromGallery = pickImage,
                    onClose = viewModel::goHome,
                    direction = settings.direction,
                    learned = learned,
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
            val back = if (s.savedId != null) viewModel::openHistory else viewModel::goHome
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
