package com.davicolzer.zureader.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davicolzer.zureader.R
import com.davicolzer.zureader.reader.ImageLoading
import com.davicolzer.zureader.reader.LiveAnalyzer
import com.davicolzer.zureader.reader.LiveFrame
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.zu.ReadingDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    onCaptured: (Bitmap) -> Unit,
    onPickFromGallery: () -> Unit,
    onClose: () -> Unit,
    direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        CameraContent(onCaptured, onPickFromGallery, onClose, direction)
    } else {
        PermissionNeeded(
            onAllow = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onGallery = onPickFromGallery,
        )
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraContent(
    onCaptured: (Bitmap) -> Unit,
    onPickFromGallery: () -> Unit,
    onClose: () -> Unit,
    direction: ReadingDirection,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val captureFailed = stringResource(R.string.camera_capture_failed)
    val unavailable = stringResource(R.string.camera_unavailable)

    val live = remember { MutableStateFlow<LiveFrame?>(null) }
    val frame by live.collectAsStateWithLifecycle()
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    // Mesma proporção (4:3) no preview e na análise: as caixas caem no lugar certo.
    val resolution = remember {
        ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolution)
            .build()
    }
    LaunchedEffect(flashOn) {
        imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    DisposableEffect(lifecycleOwner, direction) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val cameraProvider = future.get().also { provider = it }
                val preview = Preview.Builder().setResolutionSelector(resolution).build()
                preview.surfaceProvider = previewView.surfaceProvider
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor, LiveAnalyzer(direction) { live.value = it })
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar(unavailable) }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CameraOverlay(
            frame = frame,
            flashOn = flashOn,
            capturing = capturing,
            onClose = onClose,
            onToggleFlash = { flashOn = !flashOn },
            onGallery = onPickFromGallery,
            horizontal = direction == ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT,
            onShutter = {
                capturing = true
                takePhoto(context, imageCapture, onDone = { photo ->
                    capturing = false
                    onCaptured(photo)
                }, onError = {
                    capturing = false
                    scope.launch { snackbar.showSnackbar(captureFailed) }
                })
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }
}

/** Fotografa, gira para ficar em pé e reduz fora da thread principal. */
private fun takePhoto(context: Context, capture: ImageCapture, onDone: (Bitmap) -> Unit, onError: () -> Unit) {
    val worker = Executors.newSingleThreadExecutor()
    val main = ContextCompat.getMainExecutor(context)
    capture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val photo = try {
                image.use { ImageLoading.upright(it.toBitmap(), it.imageInfo.rotationDegrees) }
            } catch (e: Exception) {
                null
            }
            main.execute { if (photo != null) onDone(photo) else onError() }
            worker.shutdown()
        }

        override fun onError(exception: ImageCaptureException) {
            main.execute(onError)
            worker.shutdown()
        }
    })
}

@Composable
private fun PermissionNeeded(onAllow: () -> Unit, onOpenSettings: () -> Unit, onGallery: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.camera_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.camera_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PixelPrimaryButton(stringResource(R.string.camera_permission_allow), R.drawable.ic_pixel_camera, onAllow)
        PixelOutlinedButton(stringResource(R.string.camera_permission_settings), R.drawable.ic_pixel_settings, onOpenSettings)
        PixelOutlinedButton(stringResource(R.string.home_pick_gallery), R.drawable.ic_pixel_gallery, onGallery)
    }
}
