package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ReadingMode
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
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
}
