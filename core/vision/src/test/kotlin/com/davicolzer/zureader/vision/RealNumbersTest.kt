package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ReadingMode
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/** Tabela dos números do jogo (0 a 10), enviada pelo usuário. */
class RealNumbersTest {
    private val file = System.getProperty("zu.testdata")?.let { File(it, "numeros-0-10.png") }

    @Test
    fun `tabela de numeros do jogo no modo automatico`() {
        assumeTrue(file != null && file.isFile, "sem testdata/real/numeros-0-10.png")
        val src = ImageIO.read(file)
        val image = RgbImage(src.width, src.height, src.getRGB(0, 0, src.width, src.height, null, 0, src.width))
        val result = ZuImageReader.forChoice(ModeChoice.AUTO).read(image)
        assertEquals(ReadingMode.NUMBERS, result.mode)
        // os 11 símbolos, cada um uma vez (a ordem depende da direção de leitura)
        assertEquals("#0123456789".toList(), result.reading.text.filter { !it.isWhitespace() }.toList().sorted())
    }

    /**
     * Placas de números na biblioteca (fotos da tela): números encostados numa faixa em relevo.
     * Recortadas em volta da placa, são lidas como números. Os dígitos ainda não têm gabarito.
     */
    @Test
    fun `placas de numeros recortadas`() {
        val dir = file?.parentFile
        assumeTrue(dir != null && File(dir, "numeros-1.jpg").isFile, "sem as fotos das placas")
        val crops = mapOf(1 to listOf(0.3f, 0.46f, 0.6f, 0.62f), 2 to listOf(0.42f, 0.47f, 0.62f, 0.67f), 3 to listOf(0.45f, 0.39f, 0.65f, 0.52f))
        for ((i, c) in crops) {
            val src = ImageIO.read(File(dir, "numeros-$i.jpg"))
            val scale = minOf(1.0, 1600.0 / maxOf(src.width, src.height))
            val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
            val small = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
            small.createGraphics().apply {
                setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(src, 0, 0, w, h, null); dispose()
            }
            // foto deitada (EXIF): gira 90° no sentido horário
            val argb = small.getRGB(0, 0, w, h, null, 0, w)
            val up = IntArray(argb.size)
            for (y in 0 until h) for (x in 0 until w) up[x * h + (h - 1 - y)] = argb[y * w + x]
            val img = RgbImage(h, w, up)
            val (l, t, r, b) = c
            val crop = img.rectify(Quad(Point(l * img.width, t * img.height), Point(r * img.width, t * img.height), Point(r * img.width, b * img.height), Point(l * img.width, b * img.height)))
            val result = ZuImageReader.forChoice(ModeChoice.AUTO).read(crop)
            println("numeros-$i (recortada): ${result.mode} '${result.reading.text}'")
            assertEquals(ReadingMode.NUMBERS, result.mode, "numeros-$i")
            assertTrue(result.reading.glyphs.size >= 2, "numeros-$i: ${result.reading.text}")
        }
    }
}
