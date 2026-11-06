package com.davicolzer.zureader.vision

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Fotos reais da tela com o jogo (testdata/real). Mede o acerto por letra contra o texto
 * certo e falha se uma mudança deixar a leitura pior que o nível atual.
 */
class RealPhotosTest {

    private class Photo(
        val file: String,
        val expected: String,
        /** Quartos de volta para pôr a foto em pé (o app usa a orientação EXIF ao abrir). */
        val exifTurns: Int,
        /** Acerto mínimo por letra (nível atual, com folga pequena). */
        val minimum: Double,
        /** Recorte feito pelo usuário (esquerda, topo, direita, base) como fração da foto em pé; `null` = foto inteira. */
        val crop: List<Float>? = null,
    ) {
        val label: String get() = if (crop == null) file else "$file (recortada)"
    }

    private val photos = listOf(
        Photo("parede-escura.jpg", "THE POINTS CONSTELLATE CONSPIRE TO CREATE SHAPES SHAPES TO TESSELLATE", 0, 0.99),
        Photo("parede-clara.jpg", "THE POINTS CONSTELLATE CONSPIRE TO CREATE SHAPES SHAPES TO TESSELLATE", 1, 0.97),
        Photo("lapide-2.jpg", "JUMPS OVER THE LAZY DOG", 1, 0.77),
        Photo("lapide-1.jpg", "THE QUICK BROWN FOX", 1, 0.6),
        // recortadas no app em volta da pedra
        Photo("lapide-2.jpg", "JUMPS OVER THE LAZY DOG", 1, 0.93, crop = listOf(0.435f, 0.27f, 0.73f, 0.585f)),
        Photo("lapide-1.jpg", "THE QUICK BROWN FOX", 1, 0.8, crop = listOf(0.33f, 0.28f, 0.67f, 0.65f)),
        // balão de fala (branco sobre preto) e parede de ladrilhos em relevo
        Photo("balao-fala.jpg", "NICE TO MEET YOU", 1, 0.99),
        // prints das lápides (melhores que as fotos da tela)
        Photo("lapide-print-1.png", "JUMPS OVER THE LAZY DOG", 0, 0.77),
        Photo("lapide-print-1.png", "JUMPS OVER THE LAZY DOG", 0, 0.99, crop = listOf(0.6f, 0.12f, 0.93f, 0.85f)),
        // inteira: as folhas do cenário viram letras soltas (a variante de neon troca F por M)
        Photo("lapide-print-2.png", "THE QUICK BROWN FOX", 0, 0.65),
        Photo("lapide-print-2.png", "THE QUICK BROWN FOX", 0, 0.99, crop = listOf(0.6f, 0.12f, 0.93f, 0.85f)),
        // recorte como o do usuário no app (pedra inteira, até a grama)
        Photo("lapide-print-2.png", "THE QUICK BROWN FOX", 0, 0.99, crop = listOf(0.57f, 0.1f, 0.93f, 0.92f)),
        // letreiros de neon (só o contorno do glifo), recortados em volta de cada placa
        Photo("neon-cidade.png", "WORK", 0, 0.99, crop = listOf(0.725f, 0.285f, 0.770f, 0.565f)),
        Photo("neon-cidade.png", "EAT MEAT", 0, 0.99, crop = listOf(0.221f, 0.457f, 0.244f, 0.611f)),
        // com folga em volta da placa (antes o "GOOD" dependia de um recorte justo)
        Photo("neon-cidade.png", "GOOD", 0, 0.99, crop = listOf(0.251f, 0.45f, 0.304f, 0.7435f)),
        Photo("neon-cidade.png", "WORK", 0, 0.99, crop = listOf(0.7146f, 0.2667f, 0.7802f, 0.5833f)),
        // a cidade inteira, sem recorte: placas "ZU" (rosa), "GOOD" (laranja) e "WORK" (direita)
        Photo("neon-cidade.png", "ZU GOOD WORK", 0, 0.99),
        Photo("parede-ladrilhos.jpg", "THE POINTS CONSTELLATE CONSPIRE TO CREATE SHAPES SHAPES TO TESSELLATE", 1, 0.92),
    )

    private val dir = System.getProperty("zu.testdata")?.let(::File)

    /** Abre como o app: em pé, colorida e com no máximo 1600 px no maior lado. */
    private fun load(photo: Photo): RgbImage {
        val src = ImageIO.read(File(dir, photo.file))
        val scale = minOf(1.0, 1600.0 / maxOf(src.width, src.height))
        val w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()
        val small = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        small.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, w, h, null)
            dispose()
        }
        var argb = small.getRGB(0, 0, w, h, null, 0, w)
        var iw = w
        var ih = h
        repeat(photo.exifTurns) {
            // gira 90° no sentido horário
            val out = IntArray(argb.size)
            for (y in 0 until ih) for (x in 0 until iw) out[x * ih + (ih - 1 - y)] = argb[y * iw + x]
            argb = out
            iw = ih.also { ih = iw }
        }
        return RgbImage(iw, ih, argb)
    }

    /**
     * 1 − distância de edição / letras esperadas, ignorando espaços e quebras de linha.
     * K/Q e U/V são o mesmo glifo no Zu: qualquer uma das duas vale.
     */
    private fun accuracy(expected: String, read: String): Double {
        fun same(s: String) = s.filter(Char::isLetter).replace('Q', 'K').replace('V', 'U')
        val a = same(expected)
        val b = same(read)
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return (1.0 - d[a.length][b.length].toDouble() / a.length).coerceAtLeast(0.0)
    }

    @Test
    fun `fotos reais do jogo`() {
        assumeTrue(dir != null && dir.isDirectory, "sem testdata/real")
        // como o app no modo padrão: escrita e números ao mesmo tempo
        val reader = ZuImageReader.forChoice(ModeChoice.AUTO)
        val failures = mutableListOf<String>()
        for (photo in photos) {
            val started = System.nanoTime()
            val image = load(photo).let { img ->
                val c = photo.crop ?: return@let img
                val (l, t, r, b) = c.map { it }
                val quad = Quad(
                    Point(l * img.width, t * img.height), Point(r * img.width, t * img.height),
                    Point(r * img.width, b * img.height), Point(l * img.width, b * img.height),
                )
                img.rectify(quad)
            }
            val result = reader.read(image)
            if (result.mode != com.davicolzer.zureader.zu.ReadingMode.TEXT) failures += "${photo.label}: lida como ${result.mode}"
            val text = result.reading.text.replace('\n', ' ')
            val ms = (System.nanoTime() - started) / 1_000_000
            val acc = accuracy(photo.expected, text)
            println("${photo.label}: ${"%.0f".format(acc * 100)}% em $ms ms → '$text'")
            if (acc < photo.minimum) failures += "${photo.label}: ${"%.2f".format(acc)} < ${photo.minimum}"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
