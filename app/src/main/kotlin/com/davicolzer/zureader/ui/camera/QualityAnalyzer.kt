package com.davicolzer.zureader.ui.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.davicolzer.zureader.vision.CameraFrames
import com.davicolzer.zureader.vision.PhotoCheck
import com.davicolzer.zureader.vision.PhotoQuality

/**
 * Avalia o visor (luz, foco, tremida) a cada [intervalMs], em quadros pequenos, para dizer se
 * está bom para fotografar. Não lê os glifos: a leitura é feita só na foto.
 */
class QualityAnalyzer(
    private val intervalMs: Long = 250,
    private val onQuality: (PhotoQuality) -> Unit,
) : ImageAnalysis.Analyzer {
    private val check = PhotoCheck()
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        image.use {
            val now = SystemClock.elapsedRealtime()
            if (now - last < intervalMs) return
            last = now
            val plane = image.planes[0]
            val buffer = plane.buffer.duplicate().apply { rewind() }
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val gray = CameraFrames.fromLuma(
                bytes, image.width, image.height, plane.rowStride, plane.pixelStride,
                image.imageInfo.rotationDegrees, maxSide = PhotoCheck.FRAME_SIDE,
            )
            onQuality(check.check(gray))
        }
    }
}
