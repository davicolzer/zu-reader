package com.davicolzer.zureader.ui.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.davicolzer.zureader.vision.CameraFrames
import com.davicolzer.zureader.vision.QrCode
import com.davicolzer.zureader.vision.QrReader

/**
 * Procura um QR code nos quadros da câmera (no máximo a cada [intervalMs]) e avisa no primeiro
 * que achar: basta apontar, não precisa fotografar.
 */
class QrAnalyzer(
    private val intervalMs: Long = 250,
    private val onFound: (QrCode) -> Unit,
) : ImageAnalysis.Analyzer {
    private var last = 0L
    @Volatile private var found = false

    override fun analyze(image: ImageProxy) {
        image.use {
            if (found) return
            val now = SystemClock.elapsedRealtime()
            if (now - last < intervalMs) return
            last = now
            val plane = image.planes[0]
            val buffer = plane.buffer.duplicate().apply { rewind() }
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            // QR precisa de mais resolução que os glifos: quadros de até 1024 px
            val gray = CameraFrames.fromLuma(
                bytes, image.width, image.height, plane.rowStride, plane.pixelStride,
                image.imageInfo.rotationDegrees, maxSide = 1024,
            )
            QrReader.read(gray)?.let { code ->
                found = true
                onFound(code)
            }
        }
    }
}
