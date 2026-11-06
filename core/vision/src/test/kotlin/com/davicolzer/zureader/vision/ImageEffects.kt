package com.davicolzer.zureader.vision

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt
import kotlin.random.Random

/** Degradações para simular prints e fotos ruins. */
object ImageEffects {

    fun boxBlur(img: GrayImage, radius: Int = 1): GrayImage {
        val out = IntArray(img.pixels.size)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            var sum = 0; var n = 0
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until img.width && ny in 0 until img.height) { sum += img[nx, ny]; n++ }
            }
            out[y * img.width + x] = sum / n
        }
        return GrayImage(img.width, img.height, out)
    }

    fun noise(img: GrayImage, sigma: Double, random: Random = Random(1)): GrayImage =
        GrayImage(img.width, img.height, IntArray(img.pixels.size) {
            (img.pixels[it] + gaussian(random) * sigma).roundToInt().coerceIn(0, 255)
        })

    /** Redimensiona com interpolação bilinear. */
    fun scale(img: GrayImage, factor: Double): GrayImage = stretch(img, factor, factor)

    /** Estica (ou achata) cada eixo separadamente, com interpolação bilinear. */
    fun stretch(img: GrayImage, fx: Double, fy: Double): GrayImage {
        val w = (img.width * fx).roundToInt()
        val h = (img.height * fy).roundToInt()
        return GrayImage(w, h, IntArray(w * h) { i ->
            val sx = ((i % w) + 0.5) / fx - 0.5
            val sy = ((i / w) + 0.5) / fy - 0.5
            val x0 = sx.toInt().coerceIn(0, img.width - 1); val x1 = (x0 + 1).coerceAtMost(img.width - 1)
            val y0 = sy.toInt().coerceIn(0, img.height - 1); val y1 = (y0 + 1).coerceAtMost(img.height - 1)
            val fx = (sx - x0).coerceIn(0.0, 1.0); val fy = (sy - y0).coerceIn(0.0, 1.0)
            val top = img[x0, y0] * (1 - fx) + img[x1, y0] * fx
            val bottom = img[x0, y1] * (1 - fx) + img[x1, y1] * fx
            (top * (1 - fy) + bottom * fy).roundToInt()
        })
    }

    fun jpeg(img: GrayImage, quality: Float): GrayImage {
        val buffered = BufferedImage(img.width, img.height, BufferedImage.TYPE_BYTE_GRAY)
        buffered.raster.setPixels(0, 0, img.width, img.height, img.pixels)
        val bytes = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        ImageIO.createImageOutputStream(bytes).use { out ->
            writer.output = out
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            writer.write(null, IIOImage(buffered, null, null), params)
        }
        writer.dispose()
        val back = ImageIO.read(bytes.toByteArray().inputStream())
        val pixels = back.raster.getPixels(0, 0, img.width, img.height, IntArray(img.width * img.height))
        return GrayImage(img.width, img.height, pixels)
    }

    /** Gira [degrees] (sentido horário), interpolação bilinear, cantos preenchidos com [fill]. */
    fun rotate(img: GrayImage, degrees: Double, fill: Int): GrayImage {
        val r = Math.toRadians(degrees)
        val c = kotlin.math.cos(r); val s = kotlin.math.sin(r)
        val w = (kotlin.math.abs(img.width * c) + kotlin.math.abs(img.height * s)).roundToInt()
        val h = (kotlin.math.abs(img.width * s) + kotlin.math.abs(img.height * c)).roundToInt()
        return GrayImage(w, h, IntArray(w * h) { i ->
            val dx = i % w - w / 2.0; val dy = i / w - h / 2.0
            val sx = dx * c + dy * s + img.width / 2.0
            val sy = -dx * s + dy * c + img.height / 2.0
            val x0 = kotlin.math.floor(sx).toInt(); val y0 = kotlin.math.floor(sy).toInt()
            if (x0 < 0 || y0 < 0 || x0 + 1 >= img.width || y0 + 1 >= img.height) fill
            else {
                val fx = sx - x0; val fy = sy - y0
                val top = img[x0, y0] * (1 - fx) + img[x0 + 1, y0] * fx
                val bottom = img[x0, y0 + 1] * (1 - fx) + img[x0 + 1, y0 + 1] * fx
                (top * (1 - fy) + bottom * fy).roundToInt()
            }
        })
    }

    /** Empilha imagens (de cima para baixo), centralizadas, com [gap] pixels entre elas. */
    fun stack(images: List<GrayImage>, gap: Int, fill: Int): GrayImage {
        val w = images.maxOf { it.width }
        val h = images.sumOf { it.height } + gap * (images.size - 1)
        val p = IntArray(w * h) { fill }
        var top = 0
        for (img in images) {
            val left = (w - img.width) / 2
            for (y in 0 until img.height) for (x in 0 until img.width) p[(top + y) * w + left + x] = img[x, y]
            top += img.height + gap
        }
        return GrayImage(w, h, p)
    }

    /** Gira 90° no sentido horário. */
    fun rotate90(img: GrayImage): GrayImage {
        val w = img.height; val h = img.width
        return GrayImage(w, h, IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            img[y, img.height - 1 - x]
        })
    }

    /** Pontinhos e uma moldura grossa, como sujeira e elementos do cenário. */
    fun clutter(img: GrayImage, ink: Int, random: Random = Random(3)): GrayImage {
        val p = img.pixels.copyOf()
        repeat(40) {
            val x = random.nextInt(img.width - 2); val y = random.nextInt(img.height - 2)
            p[y * img.width + x] = ink; p[y * img.width + x + 1] = ink
        }
        for (x in 0 until img.width) for (t in 0 until 4) { p[t * img.width + x] = ink; p[(img.height - 1 - t) * img.width + x] = ink }
        for (y in 0 until img.height) for (t in 0 until 4) { p[y * img.width + t] = ink; p[y * img.width + img.width - 1 - t] = ink }
        return GrayImage(img.width, img.height, p)
    }

    /**
     * Coloca o texto no meio de um cenário: gradiente e blocos de tamanhos variados ao redor,
     * como paredes e objetos num print do jogo.
     */
    fun inScene(text: GrayImage, random: Random = Random(5)): GrayImage {
        val w = text.width * 2; val h = text.height + 200
        val p = IntArray(w * h) { i -> 150 + 60 * (i % w) / w + 20 * (i / w) / h }
        repeat(30) {
            val bw = 6 + random.nextInt(120); val bh = 6 + random.nextInt(120)
            val x = random.nextInt(w - bw); val y = random.nextInt(h - bh)
            val shade = random.nextInt(0, 120)
            for (yy in y until y + bh) for (xx in x until x + bw) p[yy * w + xx] = shade
        }
        // área limpa ao redor do texto, como a parede onde ele está escrito
        val ox = (w - text.width) / 2; val oy = 100
        for (y in 0 until text.height) for (x in 0 until text.width) p[(oy + y) * w + ox + x] = text[x, y]
        return GrayImage(w, h, p)
    }

    fun contrast(img: GrayImage, low: Int, high: Int): GrayImage =
        GrayImage(img.width, img.height, IntArray(img.pixels.size) { low + img.pixels[it] * (high - low) / 255 })

    private fun gaussian(random: Random): Double {
        var u = 0.0; var v = 0.0
        while (u == 0.0) u = random.nextDouble()
        while (v == 0.0) v = random.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }
}
