package com.davicolzer.zureader.ui.note

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AnnotationsTest {
    private val gold = 0xFFF5B700.toInt()
    private val red = 0xFFE5243B.toInt()
    private val marks = listOf(
        Mark.Arrow(Offset(10f, 10f), Offset(100f, 10f), gold),
        Mark.Shape(Offset(200f, 200f), Offset(300f, 260f), ellipse = false, color = red),
        Mark.Shape(Offset(400f, 50f), Offset(350f, 120f), ellipse = true, color = gold),
        Mark.Label(Offset(50f, 300f), "Porta; da torre\nação ünï", -1),
    )

    @Test
    fun `desenhos vao e voltam com a foto original`() {
        val back = MarkCodec.decode(MarkCodec.encode("original.jpg", marks))!!
        assertEquals("original.jpg", back.originalFile)
        assertEquals(marks, back.marks)
    }

    @Test
    fun `anotacao antiga sem desenhos guardados nao decodifica`() {
        assertNull(MarkCodec.decode(""))
        assertNull(MarkCodec.decode("zu1\n1;2;3"))
    }

    @Test
    fun `mover desloca todos os pontos`() {
        val d = Offset(5f, -3f)
        assertEquals(Mark.Arrow(Offset(15f, 7f), Offset(105f, 7f), gold), marks[0].moved(d))
        assertEquals(Mark.Shape(Offset(205f, 197f), Offset(305f, 257f), false, red), marks[1].moved(d))
        assertEquals(Offset(55f, 297f), (marks[3].moved(d) as Mark.Label).at)
    }

    @Test
    fun `toque acha o desenho de cima`() {
        // perto da linha da seta, longe dela
        assertEquals(0, hitMark(marks, Offset(60f, 14f), tolerance = 8f, textSize = 40f))
        assertNull(hitMark(marks, Offset(60f, 50f), tolerance = 8f, textSize = 40f))
        // dentro do retângulo e do círculo (cantos trocados)
        assertEquals(1, hitMark(marks, Offset(250f, 230f), tolerance = 8f, textSize = 40f))
        assertEquals(2, hitMark(marks, Offset(370f, 100f), tolerance = 8f, textSize = 40f))
        // no texto
        assertEquals(3, hitMark(marks, Offset(80f, 320f), tolerance = 8f, textSize = 40f))
        // dois desenhos no mesmo lugar: vale o de cima (o último)
        val stacked = marks + Mark.Shape(Offset(240f, 220f), Offset(260f, 240f), false, gold)
        assertEquals(4, hitMark(stacked, Offset(250f, 230f), tolerance = 8f, textSize = 40f))
    }

    @Test
    fun `trocar a cor mantem o desenho`() {
        assertEquals(Mark.Shape(Offset(200f, 200f), Offset(300f, 260f), false, gold), marks[1].recolored(gold))
    }

    @Test
    fun `cabeca da seta aponta para tras a partir da ponta`() {
        val (l, r) = arrowHead(Offset(0f, 0f), Offset(100f, 0f), strokeWidth = 4f)
        // 20 px (5x o traço) para trás, a 30° de cada lado
        assertEquals(100f - 20f * 0.866f, l.x, 0.01f)
        assertEquals(100f - 20f * 0.866f, r.x, 0.01f)
        assertEquals(10f, kotlin.math.abs(l.y), 0.01f)
        assertEquals(-l.y, r.y, 0.01f)
        // seta curta: a cabeça não passa de metade dela
        val (s, _) = arrowHead(Offset(0f, 0f), Offset(10f, 0f), strokeWidth = 4f)
        assertEquals(10f - 5f * 0.866f, s.x, 0.01f)
    }

    @Test
    fun `recortar desloca os desenhos e tira os que ficaram de fora`() {
        // recorte de (150, 0) com 300 x 300: a seta (x 10..100) sai; o retângulo e o círculo ficam
        val cut = cropMarks(marks, left = 150f, top = 0f, width = 300, height = 300, textSize = 40f)
        assertEquals(listOf(marks[1].moved(Offset(-150f, 0f)), marks[2].moved(Offset(-150f, 0f))), cut)
    }

    @Test
    fun `reduzir a foto reduz os desenhos junto`() {
        assertEquals(Mark.Arrow(Offset(5f, 5f), Offset(50f, 5f), gold), marks[0].scaled(0.5f))
        assertEquals(Mark.Shape(Offset(100f, 100f), Offset(150f, 130f), false, red), marks[1].scaled(0.5f))
        assertEquals(Offset(25f, 150f), (marks[3].scaled(0.5f) as Mark.Label).at)
    }
}
