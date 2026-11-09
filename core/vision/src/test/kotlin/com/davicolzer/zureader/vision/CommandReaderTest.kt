package com.davicolzer.zureader.vision

import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class CommandReaderTest {
    private val dir = System.getProperty("zu.testdata")?.let(::File)

    private fun load(name: String): RgbImage? {
        val file = dir?.let { File(it, name) }?.takeIf { it.isFile } ?: return null
        val src = ImageIO.read(file)
        return RgbImage(src.width, src.height, src.getRGB(0, 0, src.width, src.height, null, 0, src.width))
    }

    @Test
    fun `codigo de ida e volta`() {
        val reading = CommandReading(listOf(CommandPiece(Tetromino.T, 2), CommandPiece(Tetromino.O), CommandPiece(null)))
        assertEquals("T2 O0 ?", reading.code)
        assertEquals(reading, CommandReading.parse(reading.code))
    }

    @Test
    fun `cada peca tem 4 celulas e as rotacoes giram no sentido horario`() {
        // T base: três em cima e um no meio embaixo (aponta para baixo); um quarto de volta: aponta para a esquerda
        assertEquals(listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1)), CommandPiece(Tetromino.T, 0).cells)
        assertEquals(listOf(Cell(0, 1), Cell(1, 0), Cell(1, 1), Cell(2, 1)), CommandPiece(Tetromino.T, 1).cells)
        for (shape in Tetromino.entries) for (q in 0..3) assertEquals(4, CommandPiece(shape, q).cells.size)
    }

    @Test
    fun `parede roxa - coluna de pecas S e Z escuras`() {
        val image = load("comandos-parede-roxa.jpg")
        assumeTrue(image != null, "sem testdata/real")
        val reading = CommandReader.read(image!!)
        println("parede roxa: ${reading.code}")
        assertEquals(8, reading.recognized)
        // na foto: as peças se alternam e as duas últimas são iguais
        val p = reading.pieces
        assertEquals(listOf(p[0], p[1], p[0], p[1], p[0], p[1], p[0], p[0]), p)
    }

    @Test
    fun `mural - a mesma sequencia em linha e em coluna`() {
        val row = load("comandos-mural-linha.jpg")
        val column = load("comandos-mural-coluna.jpg")
        assumeTrue(row != null && column != null, "sem testdata/real")
        val inRow = CommandReader.read(row!!)
        val inColumn = CommandReader.read(column!!)
        println("mural, linha: ${inRow.code}")
        println("mural, coluna: ${inColumn.code}")
        assertEquals("T2 T1 S0 O0 Z0 T3 O0 T0", inRow.code)
        // a coluna (deitada para ler) dá as mesmas peças; o recorte de teste não pega a primeira
        assertEquals(inRow.pieces.drop(1), inColumn.pieces.takeLast(7))
    }

    @Test
    fun `mural - pecas coladas (ainda experimental)`() {
        val image = load("comandos-mural-coladas.jpg")
        assumeTrue(image != null, "sem testdata/real")
        println("mural, coladas: ${CommandReader.read(image!!).code}")
    }
}
