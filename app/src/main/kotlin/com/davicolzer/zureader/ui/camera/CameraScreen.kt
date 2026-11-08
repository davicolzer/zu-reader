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
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import java.io.File
import com.davicolzer.zureader.reader.newVideoFile
import androidx.compose.runtime.mutableLongStateOf
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recording
import androidx.camera.video.Recorder
import androidx.camera.video.QualitySelector
import androidx.camera.video.Quality
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.core.Camera
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.davicolzer.zureader.R
import com.davicolzer.zureader.reader.CameraPurpose
import com.davicolzer.zureader.reader.QrAnalyzer
import com.davicolzer.zureader.vision.QrCode
import com.davicolzer.zureader.reader.ImageLoading
import com.davicolzer.zureader.reader.QualityAnalyzer
import com.davicolzer.zureader.vision.PhotoQuality
import com.davicolzer.zureader.ui.components.PixelOutlinedButton
import com.davicolzer.zureader.ui.components.PixelPrimaryButton
import com.davicolzer.zureader.zu.ReadingDirection
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    onCaptured: (Bitmap) -> Unit,
    onPickFromGallery: () -> Unit,
    onClose: () -> Unit,
    direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
    /** Para que a câmera foi aberta: ler glifos (caixas ao vivo), anotar uma foto ou ler um QR code (ao vivo). */
    purpose: CameraPurpose = CameraPurpose.READ,
    onQrFound: (QrCode, Bitmap?) -> Unit = { _, _ -> },
    /** Vídeo gravado (modo [CameraPurpose.VIDEO]): o arquivo, no cache do app. */
    onVideoRecorded: (File) -> Unit = {},
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        CameraContent(onCaptured, onPickFromGallery, onClose, direction, purpose, onQrFound, onVideoRecorded)
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
    purpose: CameraPurpose,
    onQrFound: (QrCode, Bitmap?) -> Unit,
    onVideoRecorded: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val captureFailed = stringResource(R.string.camera_capture_failed)
    val unavailable = stringResource(R.string.camera_unavailable)

    var quality by remember { mutableStateOf<PhotoQuality?>(null) }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    // vídeo: gravação em andamento e quanto já foi gravado
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val recordFailed = stringResource(R.string.video_record_failed)
    // vídeo em HD (ou o mais perto disso que a câmera tiver), sem som
    val videoCapture = remember {
        VideoCapture.withOutput(
            Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                .build(),
        )
    }

    val previewView = remember {
        // TextureView (COMPATIBLE): o preview é desenhado junto com a tela e acompanha o tamanho
        // dela até a borda de baixo (o SurfaceView padrão podia ficar com o tamanho da primeira
        // medida, deixando uma faixa vazia embaixo, e não acompanha a transição de telas)
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
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
    // A foto sai em pé conforme o celular é segurado, mesmo com a rotação da tela travada:
    // o sensor de movimento diz a posição e a câmera grava a foto nessa orientação.
    // quanto girar textos e ícones para ficarem em pé para quem segura o celular deitado
    var turn by remember { mutableIntStateOf(0) }
    DisposableEffect(imageCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                // gravando vídeo: a posição fica travada como estava ao começar (nem vídeo nem tela giram)
                if (recording != null) return
                // celular deitado para a direita (topo à direita) → conteúdo gira −90°; para a esquerda → +90°
                turn = when (degrees) {
                    in 60 until 120 -> -90
                    in 240 until 300 -> 90
                    in 0 until 30, in 330..360, in 150 until 210 -> 0
                    else -> turn
                }
                val rotation = when (degrees) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture.targetRotation = rotation
                // o vídeo também sai em pé (escolhido antes de começar a gravar)
                videoCapture.targetRotation = rotation
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    LaunchedEffect(flashOn) {
        imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        // no vídeo, o "flash" é a lanterna acesa enquanto grava
        if (purpose == CameraPurpose.VIDEO) camera?.cameraControl?.enableTorch(flashOn)
    }

    DisposableEffect(lifecycleOwner, purpose) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val cameraProvider = future.get().also { provider = it }
                // vídeo: o preview na proporção do vídeo (16:9), para o enquadramento ser o gravado
                val preview = if (purpose == CameraPurpose.VIDEO) Preview.Builder().build() else Preview.Builder().setResolutionSelector(resolution).build()
                preview.surfaceProvider = previewView.surfaceProvider
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val main = ContextCompat.getMainExecutor(context)
                val analyzer = when (purpose) {
                    // achou o QR: tira uma foto colorida para guardar junto (se falhar, segue sem foto)
                    CameraPurpose.QR -> QrAnalyzer { code ->
                        main.execute {
                            takePhoto(context, imageCapture, onDone = { photo -> onQrFound(code, photo) }, onError = { onQrFound(code, null) })
                        }
                    }
                    // ler Zu: só diz se o visor está bom para fotografar (a leitura é feita na foto)
                    CameraPurpose.READ -> QualityAnalyzer { q -> main.execute { quality = q } }
                    // anotar e vídeo: a câmera só fotografa ou grava, não precisa analisar
                    CameraPurpose.NOTE, CameraPurpose.VIDEO -> null
                }
                analyzer?.let { analysis.setAnalyzer(analysisExecutor, it) }
                cameraProvider.unbindAll()
                camera = if (purpose == CameraPurpose.VIDEO) {
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture)
                } else {
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
                }
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar(unavailable) }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            recording?.stop()
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().clipToBounds())
        CameraOverlay(
            quality = quality,
            turn = turn,
            flashOn = flashOn,
            capturing = capturing,
            onClose = onClose,
            onToggleFlash = { flashOn = !flashOn },
            onGallery = onPickFromGallery,
            horizontal = direction == ReadingDirection.HORIZONTAL_LEFT_TO_RIGHT,
            purpose = purpose,
            recordingMs = if (recording != null) recordedMs else null,
            onShutter = shutter@{
                if (purpose == CameraPurpose.VIDEO) {
                    // vídeo: toca para começar; toca de novo (ou 1 minuto) para parar
                    recording?.let { it.stop(); return@shutter }
                    val file = newVideoFile(context)
                    val options = FileOutputOptions.Builder(file).setDurationLimitMillis(MAX_VIDEO_MS).build()
                    recordedMs = 0L
                    recording = videoCapture.output.prepareRecording(context, options)
                        .start(ContextCompat.getMainExecutor(context)) { event ->
                            when (event) {
                                is VideoRecordEvent.Status -> recordedMs = event.recordingStats.recordedDurationNanos / 1_000_000
                                is VideoRecordEvent.Finalize -> {
                                    recording = null
                                    val ok = !event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                                    if (ok && file.length() > 0) {
                                        onVideoRecorded(file)
                                    } else {
                                        file.delete()
                                        scope.launch { snackbar.showSnackbar(recordFailed) }
                                    }
                                }
                                else -> Unit
                            }
                        }
                    return@shutter
                }
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

/** Duração máxima de um vídeo. */
private const val MAX_VIDEO_MS = 60_000L

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
