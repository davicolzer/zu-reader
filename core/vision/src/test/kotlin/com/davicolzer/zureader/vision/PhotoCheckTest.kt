package com.davicolzer.zureader.vision

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class PhotoCheckTest {
    private val dir = System.getProperty("zu.testdata")?.let(::File)

    /** Foto reduzida ao tamanho do quadro analisado no visor. */
    private fun frame(file: File): GrayImage {
        val src = ImageIO.read(file)
        val s = PhotoCheck.FRAME_SIDE.toDouble() / maxOf(src.width, src.height)
        val w = (src.width * s).toInt()
        val h = (src.height * s).toInt()
        val small = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        small.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, w, h, null)
            dispose()
        }
        return RgbImage(w, h, small.getRGB(0, 0, w, h, null, 0, w)).luma()
    }

    private fun photos(): List<File> {
        assumeTrue(dir?.isDirectory == true)
        return dir!!.listFiles()!!.filter { it.extension in setOf("jpg", "png") }.sorted()
    }

    private fun stillQuality(image: GrayImage): PhotoQuality {
        val (contrast, sharpness) = PhotoCheck.contrastAndSharpness(image)
        return PhotoCheck.quality(FrameStats(contrast, sharpness, 0f))
    }

    @Test
    fun `fotos reais nitidas sao boas`() {
        for (f in photos()) assertEquals(PhotoQuality.GOOD, stillQuality(frame(f)), f.name)
    }

    @Test
    fun `fotos desfocadas nao sao boas`() {
        for (f in photos()) assertNotEquals(PhotoQuality.GOOD, stillQuality(frame(f).boxBlur(3)), f.name)
        val poor = photos().count { stillQuality(frame(it).boxBlur(3)) == PhotoQuality.POOR }
        assert(poor >= photos().size / 2) { "só $poor fotos bem borradas foram ruins" }
    }

    @Test
    fun `tela escura sem nada e ruim`() {
        val dark = GrayImage(320, 240, IntArray(320 * 240) { 12 + it % 3 })
        assertEquals(PhotoQuality.POOR, stillQuality(dark))
    }

    @Test
    fun `celular tremendo e ruim e parado volta a ser bom`() {
        val sharp = ZuRenderer.render("OPEN THE DOOR", blockPx = 6)
        val check = PhotoCheck()
        val moved = GrayImage(sharp.width, sharp.height, IntArray(sharp.pixels.size) { i ->
            val x = i % sharp.width
            sharp[(x + 25).coerceAtMost(sharp.width - 1), i / sharp.width]
        })
        // tremendo: cada quadro diferente do anterior
        val shaking = (1..4).map { check.check(if (it % 2 == 0) sharp else moved) }
        assertEquals(PhotoQuality.POOR, shaking.last())
        // parado: depois de alguns quadros iguais, volta a ser bom
        val still = (1..4).map { check.check(sharp) }
        assertEquals(PhotoQuality.GOOD, still.last())
    }
}
