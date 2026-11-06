package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ZuAlphabet
import com.davicolzer.zureader.zu.ZuLayout
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.DataOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.random.Random
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Gera os dados de treino da rede de glifos (não roda no CI). Para gerar:
 * `./gradlew :core:vision:test --tests '*GlyphNetDataGenerator*' -Dzu.gen=<pasta>`
 * (o Gradle repassa a propriedade via ZU_GEN). Treino: `python3 tools/train_glyphnet.py <pasta>`.
 *
 * Classes: índice do glifo em [ZuAlphabet.glyphs] (0–23) e 24 = "não é glifo".
 */
class GlyphNetDataGenerator {
    private val out = System.getenv("ZU_GEN")?.let(::File)
    private val notGlyph = ZuAlphabet.glyphs.size
    private val words = listOf(
        "THE", "QUICK", "BROWN", "FOX", "JUMPS", "OVER", "LAZY", "DOG", "POINTS", "SHAPES", "CREATE", "OPEN",
        "DOOR", "WATER", "TREE", "LIFE", "OWL", "LOOK", "WALK", "AROUND", "GIVE", "KEY", "VERY", "QUIET",
    )

    private class Writer(file: File) {
        private val stream = DataOutputStream(file.outputStream().buffered())
        var count = 0
        fun add(x: FloatArray, label: Int) {
            for (v in x) stream.writeFloat(v)
            stream.writeInt(label)
            count++
        }
        fun close() = stream.close()
    }

    @Test
    fun gerar() {
        assumeTrue(out != null, "defina ZU_GEN para gerar")
        out!!.mkdirs()
        val random = Random(2026)
        val train = Writer(File(out, "sintetico.bin"))
        repeat(1400) { i -> synthetic(random, train, hard = i % 2 == 0) }
        train.close()
        println("sintético: ${train.count} amostras")

        val real = Writer(File(out, "real.bin"))
        realPhotos(real)
        real.close()
        println("real: ${real.count} amostras")
    }

    /** Uma imagem com texto aleatório, degradada; recortes com a caixa certa (com erro de posição) e negativos. */
    private fun synthetic(random: Random, writer: Writer, hard: Boolean) {
        val text = (1..random.nextInt(2, 6)).joinToString(" ") { words.random(random) }
        val block = random.nextInt(4, 13)
        val ink = random.nextInt(0, 110)
        val paper = random.nextInt(ink + 40, 256)
        val darkInk = random.nextBoolean()
        val glyphs = ZuLayout.compose(text, maxPerLine = random.nextInt(4, 9), size = 5f * block)
        var img = renderAt(glyphs, block, if (darkInk) ink else paper, if (darkInk) paper else ink)
        // degradações
        if (random.nextFloat() < 0.7f) img = ImageEffects.boxBlur(img, random.nextInt(1, if (hard) 3 else 2))
        if (random.nextFloat() < 0.6f) img = ImageEffects.noise(img, random.nextDouble(2.0, if (hard) 30.0 else 12.0), random)
        if (random.nextFloat() < 0.5f) img = ImageEffects.jpeg(img, random.nextDouble(0.3, 0.9).toFloat())
        if (random.nextFloat() < 0.3f) img = ImageEffects.contrast(img, random.nextInt(0, 80), random.nextInt(170, 256))
        val margin = 4 * block
        val size = 5f * block
        val minX = glyphs.minOf { it.x - size / 2 }
        val minY = glyphs.minOf { it.y - size / 2 }
        for (g in glyphs) {
            val label = ZuAlphabet.glyphs.indexOfFirst { it.pattern == g.bits }
            val cx = g.x - minX + margin
            val cy = g.y - minY + margin
            repeat(3) {
                // caixa com erro de posição e tamanho, como a segmentação real
                val j = if (hard) 0.18f else 0.1f
                val w = size * (1 + random.nextFloat() * 2 * j - j)
                val h = size * (1 + random.nextFloat() * 2 * j - j)
                val x = cx + size * (random.nextFloat() * 2 * j - j)
                val y = cy + size * (random.nextFloat() * 2 * j - j)
                writer.add(GlyphCrop.extract(img, box(x, y, w, h, img), darkInk), label)
            }
            // negativo: caixa deslocada meio glifo ou mais (pega pedaços de dois glifos)
            val dx = size * (0.45f + random.nextFloat() * 0.3f) * if (random.nextBoolean()) 1 else -1
            val dy = size * (0.45f + random.nextFloat() * 0.3f) * if (random.nextBoolean()) 1 else -1
            writer.add(GlyphCrop.extract(img, box(cx + dx, cy + dy, size, size, img), darkInk), notGlyph)
        }
        // negativos: cenário sem texto (blocos e gradiente)
        repeat(6) {
            val scene = ImageEffects.inScene(GrayImage.filled(4 * block * 5, 4 * block * 5, paper), random)
            val s = size * (0.8f + random.nextFloat() * 0.6f)
            val x = random.nextFloat() * (scene.width - s) + s / 2
            val y = random.nextFloat() * (scene.height - s) + s / 2
            writer.add(GlyphCrop.extract(scene, box(x, y, s, s, scene), random.nextBoolean()), notGlyph)
        }
    }

    private fun renderAt(glyphs: List<com.davicolzer.zureader.zu.PlacedGlyph>, block: Int, ink: Int, paper: Int): GrayImage {
        val size = 5 * block
        val margin = 4 * block
        val minX = glyphs.minOf { it.x - size / 2f }
        val minY = glyphs.minOf { it.y - size / 2f }
        val w = (glyphs.maxOf { it.x + size / 2f } - minX).roundToInt() + 2 * margin
        val h = (glyphs.maxOf { it.y + size / 2f } - minY).roundToInt() + 2 * margin
        val p = IntArray(w * h) { paper }
        for (g in glyphs) {
            val left = (g.x - size / 2f - minX).roundToInt() + margin
            val top = (g.y - size / 2f - minY).roundToInt() + margin
            for (r in 0 until 5) for (c in 0 until 5) if (g.bits[r, c]) {
                for (y in top + r * block until top + (r + 1) * block) for (x in left + c * block until left + (c + 1) * block) p[y * w + x] = ink
            }
        }
        return GrayImage(w, h, p)
    }

    private fun box(cx: Float, cy: Float, w: Float, h: Float, img: GrayImage) = PixelBox(
        (cx - w / 2).toInt().coerceIn(0, img.width - 2), (cy - h / 2).toInt().coerceIn(0, img.height - 2),
        (cx + w / 2).toInt().coerceIn(2, img.width), (cy + h / 2).toInt().coerceIn(2, img.height),
    )

    /** Glifos das paredes (lidas 100%/98%), com a letra certa do texto; as lápides ficam de fora para avaliar. */
    private fun realPhotos(writer: Writer) {
        val dir = File(System.getProperty("zu.testdata") ?: return)
        val expected = "THE POINTS CONSTELLATE CONSPIRE TO CREATE SHAPES SHAPES TO TESSELLATE".filter(Char::isLetter)
        for ((name, turns) in listOf("parede-escura.jpg" to 0, "parede-clara.jpg" to 1)) {
            val src = ImageIO.read(File(dir, name))
            val scale = minOf(1.0, 1600.0 / maxOf(src.width, src.height))
            val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
            val small = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also {
                it.createGraphics().apply { setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(src, 0, 0, w, h, null); dispose() }
            }
            var gray = GrayImage.fromArgb(w, h, small.getRGB(0, 0, w, h, null, 0, w))
            repeat(turns) { gray = ImageEffects.rotate90(gray) }
            val reading = ZuImageReader().read(gray)
            if (reading.reading.glyphs.size != expected.length || reading.skewDegrees != 0f) continue
            reading.reading.glyphs.forEachIndexed { i, g ->
                val label = ZuAlphabet.glyphs.indexOfFirst { expected[i] in it.letters }
                val s = g.source
                for (jitter in listOf(0f, 0.06f, -0.06f)) {
                    val b = box(s.x + s.width * jitter, s.y - s.height * jitter, s.width * (1 + jitter), s.height * (1 - jitter), gray)
                    writer.add(GlyphCrop.extract(gray, b, reading.inkIsDark), label)
                }
            }
        }
    }
}
