package com.davicolzer.zureader.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewportTest {
    // tela 400×800, foto 1000×1000: escala 0,4, foto de 400×400 centralizada na vertical
    private val fit = Viewport.fit(400f, 800f, 1000, 1000)

    @Test
    fun `sem zoom a foto inteira aparece centralizada`() {
        assertEquals(0.4f, fit.scale, 1e-6f)
        assertEquals(0f, fit.left, 1e-3f); assertEquals(200f, fit.top, 1e-3f)
        assertEquals(Point(500f, 500f), fit.toImage(200f, 400f))
    }

    @Test
    fun `pinça amplia em volta dos dedos`() {
        val z = fit.zoomedAt(200f, 400f, 4f)
        assertEquals(4f, z.zoom)
        // o ponto entre os dedos continua mostrando o mesmo pixel da foto
        val p = z.toImage(200f, 400f)
        assertEquals(500f, p.x, 1e-2f); assertEquals(500f, p.y, 1e-2f)
    }

    @Test
    fun `zoom tem limites e a foto ampliada cobre a tela`() {
        assertEquals(1f, fit.zoomedAt(200f, 400f, 0.2f).zoom)
        assertEquals(Viewport.MAX_ZOOM, fit.zoomedAt(200f, 400f, 100f).zoom)
        val z = fit.zoomedAt(0f, 0f, 4f).panned(10_000f, 10_000f)
        assertEquals(0f, z.left, 1e-3f); assertEquals(0f, z.top, 1e-3f)
        val far = z.panned(-1e6f, -1e6f)
        assertEquals(400f - 1000 * far.scale, far.left, 1e-2f); assertEquals(800f - 1000 * far.scale, far.top, 1e-2f)
    }

    @Test
    fun `ida e volta entre tela e foto`() {
        val z = fit.zoomedAt(123f, 456f, 2.5f).panned(-30f, 12f)
        val p = Point(321f, 654f)
        val back = z.toView(p).let { z.toImage(it.x, it.y) }
        assertEquals(p.x, back.x, 1e-2f); assertEquals(p.y, back.y, 1e-2f)
    }

    @Test
    fun `area que cresce mantem zoom e o ponto do centro`() {
        val z = fit.zoomedAt(200f, 400f, 3f)
        val before = z.toImage(200f, 400f)
        val big = z.resized(400f, 1000f)
        assertEquals(3f, big.zoom)
        val after = big.toImage(200f, 500f)
        assertEquals(before.x, after.x, 0.5f); assertEquals(before.y, after.y, 0.5f)
        // perto da borda, a foto continua presa (sem faixa vazia)
        val edge = fit.zoomedAt(0f, 200f, 3f).resized(400f, 1200f)
        assertEquals(0f, edge.top, 0.01f)
    }
}
