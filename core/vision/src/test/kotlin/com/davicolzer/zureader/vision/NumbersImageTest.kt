package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuNumerals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fase 8: números do Zu em imagens. */
class NumbersImageTest {

    private val numbers = ZuImageReader.forMode(ReadingMode.NUMBERS)

    private fun render(text: String, blockPx: Int = 8) = ZuRenderer.render(text, maxPerLine = 4, blockPx = blockPx, glyphSet = ZuNumerals)

    @Test
    fun `imagem limpa`() {
        val r = numbers.read(render("3#7 12 0"))
        assertEquals("3#7 12 0", r.reading.text)
        assertEquals(ReadingMode.NUMBERS, r.mode)
        assertEquals("3 10 7 / 1 2 / 0", r.mode.display(r.reading.text))
    }

    @Test
    fun `borrao, ruido e JPEG`() {
        val img = ImageEffects.jpeg(ImageEffects.noise(ImageEffects.boxBlur(render("2468 1357", blockPx = 10), 1), 12.0), 0.6f)
        assertEquals("2468 1357", numbers.read(img).reading.text)
    }

    @Test
    fun `imagem colorida`() {
        val gray = render("90#")
        val rgb = RgbImage(gray.width, gray.height, IntArray(gray.pixels.size) {
            if (gray.pixels[it] < 128) (0xFF shl 24) or (90 shl 16) or (40 shl 8) or 140 else (0xFF shl 24) or (200 shl 16) or (170 shl 8) or 240
        })
        assertEquals("90#", numbers.read(rgb).reading.text)
    }

    @Test
    fun `modo escrita nao devolve numeros`() {
        val text = ZuImageReader.forMode(ReadingMode.TEXT).read(render("1234")).reading.text
        assertTrue(text.none { it.isDigit() || it == '#' }, "lido '$text'")
    }

    @Test
    fun `numeros salvos guardam o modo`() {
        val r = numbers.read(render("42"))
        val back = ReadingCodec.decode(ReadingCodec.encode(r))
        assertEquals(ReadingMode.NUMBERS, back.mode)
        assertEquals("42", back.reading.text)
    }

    @Test
    fun `leitura salva antes do modo numeros abre como escrita`() {
        val old = ReadingCodec.encode(ZuImageReader().read(ZuRenderer.render("LOOK"))).lines().toMutableList()
        old[1] = old[1].substringBeforeLast(';') // tira o campo do modo
        assertEquals(ReadingMode.TEXT, ReadingCodec.decode(old.joinToString("\n")).mode)
    }

    /**
     * Placa de contas da biblioteca: em cada coluna, 3 números encostados numa faixa (bordas
     * divididas), o sinal de igual (dois traços) e o resultado. Claro sobre escuro.
     * [border] < 1 deixa as bordas entre números mais finas, como na foto.
     */
    private fun sumsPlaque(columns: List<List<Char>>, block: Int = 10, border: Float = 1f): GrayImage {
        val w = (columns.size * 9 + 4) * block
        val h = 30 * block
        val p = IntArray(w * h) { 40 }
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int) { for (y in y0 until y1) for (x in x0 until x1) p[y * w + x] = 235 }
        columns.forEachIndexed { i, col ->
            val left = (2 + i * 9) * block
            // 3 números: cada um 5×5 blocos, a borda de baixo de um é a de cima do próximo
            var top = 2f * block
            for (k in 0 until 3) {
                val bits = ZuNumerals.glyphFor(col[k]).pattern
                for (r in 0 until 5) for (c in 0 until 5) if (bits[r, c]) {
                    val rowTop = top + (if (r == 0) 0f else (r - 1 + border) * block)
                    val rowH = if (r == 0 || r == 4) border * block else block.toFloat()
                    fill(left + c * block, rowTop.toInt(), left + (c + 1) * block, (rowTop + rowH).toInt())
                }
                top += (3 + border) * block
            }
            val stripBottom = (top + border * block).toInt()
            // sinal de igual: traço, vão, traço (1 bloco cada)
            fill(left, stripBottom + 2 * block, left + 5 * block, stripBottom + 3 * block)
            fill(left, stripBottom + 4 * block, left + 5 * block, stripBottom + 5 * block)
            // resultado
            val bits = ZuNumerals.glyphFor(col[3]).pattern
            val rTop = stripBottom + 7 * block
            for (r in 0 until 5) for (c in 0 until 5) if (bits[r, c]) fill(left + c * block, rTop + r * block, left + (c + 1) * block, rTop + (r + 1) * block)
        }
        return GrayImage(w, h, p)
    }

    @Test
    fun `placa de contas com sinal de igual`() {
        // colunas da direita para a esquerda: 1+4+5=10, 2+3+5=10, 1+2+7=10
        val plaque = sumsPlaque(listOf(listOf('1', '2', '7', '#'), listOf('2', '3', '5', '#'), listOf('1', '4', '5', '#')))
        for (img in listOf(plaque, ImageEffects.jpeg(ImageEffects.boxBlur(plaque, 1), 0.7f))) {
            val result = ZuImageReader.forChoice(ModeChoice.AUTO).read(img)
            assertEquals(ReadingMode.NUMBERS, result.mode)
            assertEquals("145=# 235=# 127=#", result.reading.text)
            assertEquals("1 4 5 = 10 / 2 3 5 = 10 / 1 2 7 = 10", ReadingMode.NUMBERS.display(result.reading.text))
        }
        // bordas entre números mais finas (a faixa mede menos que 13 blocos)
        assertEquals("145=# 235=# 127=#", ZuImageReader.forChoice(ModeChoice.NUMBERS).read(sumsPlaque(listOf(listOf('1', '2', '7', '#'), listOf('2', '3', '5', '#'), listOf('1', '4', '5', '#')), border = 0.6f)).reading.text)
    }
}
