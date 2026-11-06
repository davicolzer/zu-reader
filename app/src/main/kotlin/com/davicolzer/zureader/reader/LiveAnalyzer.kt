package com.davicolzer.zureader.reader

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.davicolzer.zureader.vision.CameraFrames
import com.davicolzer.zureader.vision.ModeChoice
import com.davicolzer.zureader.vision.ZuImageReader
import com.davicolzer.zureader.zu.ReadingDirection
import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuGlyph

/** Glifo visto no visor, em pixels do quadro analisado (já girado como o usuário vê). */
data class LiveGlyph(val x: Float, val y: Float, val width: Float, val height: Float, val lowConfidence: Boolean)

/** Resultado de um quadro do visor. */
data class LiveFrame(val width: Int, val height: Int, val glyphs: List<LiveGlyph>, val text: String, val mode: ReadingMode = ReadingMode.TEXT)

/**
 * Lê os quadros da câmera em resolução reduzida, no máximo a cada [intervalMs],
 * só para desenhar as caixas ao vivo. A leitura completa é feita na foto.
 */
class LiveAnalyzer(
    direction: ReadingDirection = ReadingDirection.VERTICAL_RIGHT_TO_LEFT,
    learned: List<ZuGlyph> = emptyList(),
    mode: ModeChoice = ModeChoice.AUTO,
    private val intervalMs: Long = 300,
    private val onFrame: (LiveFrame) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = ZuImageReader.forChoice(mode, direction, learned)
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
                image.imageInfo.rotationDegrees, maxSide = 640,
            )
            val result = reader.read(gray, deskew = false, thorough = false)
            onFrame(
                LiveFrame(
                    gray.width, gray.height,
                    result.reading.glyphs.map { g ->
                        LiveGlyph(g.source.x, g.source.y, g.source.width, g.source.height, g.isLowConfidence)
                    },
                    result.reading.text,
                    result.mode,
                ),
            )
        }
    }
}
