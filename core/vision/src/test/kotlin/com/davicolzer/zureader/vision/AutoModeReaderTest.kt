package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ReadingMode
import com.davicolzer.zureader.zu.ZuNumerals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoModeReaderTest {
    private val auto = ZuImageReader.forChoice(ModeChoice.AUTO)

    @Test
    fun `automatico reconhece escrita`() {
        for (text in listOf("THE QUICK BROWN FOX", "OPEN THE DOOR", "SHAPES TO TESSELLATE", "OWL")) {
            val result = auto.read(ZuRenderer.render(text))
            assertEquals(ReadingMode.TEXT, result.mode, text)
            assertEquals(text, result.reading.text)
        }
    }

    @Test
    fun `automatico reconhece numeros`() {
        for (numbers in listOf("3#7 12", "0123 4567", "89#", "5")) {
            val result = auto.read(ZuRenderer.render(numbers, maxPerLine = 4, glyphSet = ZuNumerals))
            assertEquals(ReadingMode.NUMBERS, result.mode, numbers)
            assertEquals(numbers, result.reading.text)
        }
    }

    @Test
    fun `B e 0 sao o par mais parecido e nao confundem o modo`() {
        // nenhuma letra tem moldura completa; o B fica a 2 quadradinhos do 0
        for (text in listOf("BOB", "BIG BLUE BOX", "B")) assertEquals(ReadingMode.TEXT, auto.read(ZuRenderer.render(text)).mode, text)
        for (numbers in listOf("00", "0 0 1", "0")) {
            assertEquals(ReadingMode.NUMBERS, auto.read(ZuRenderer.render(numbers, maxPerLine = 4, glyphSet = ZuNumerals)).mode, numbers)
        }
    }

    @Test
    fun `automatico com ruido e cor`() {
        val text = ImageEffects.jpeg(ImageEffects.noise(ImageEffects.boxBlur(ZuRenderer.render("JUMPS OVER THE LAZY DOG"), 1), 15.0), 0.6f)
        assertEquals(ReadingMode.TEXT, auto.read(text).mode)
        val numbers = ImageEffects.jpeg(ImageEffects.noise(ZuRenderer.render("47 #3", maxPerLine = 4, glyphSet = ZuNumerals), 15.0), 0.6f)
        assertEquals(ReadingMode.NUMBERS, auto.read(RgbImage.fromGray(numbers)).mode)
    }

    @Test
    fun `um modo so e o leitor do modo`() {
        assertEquals(ReadingMode.NUMBERS, ZuImageReader.forChoice(ModeChoice.NUMBERS).read(ZuRenderer.render("THE")).mode)
    }
}
