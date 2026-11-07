package com.davicolzer.zureader.vision

/**
 * Como a foto aparece na tela de recorte: [zoom] 1 = foto inteira visível (centralizada);
 * mais que isso amplia. [left]/[top] = onde o canto da foto fica na tela, em pixels da tela.
 * Ampliada, a foto sempre cobre a área toda (não sobra vão nas bordas).
 */
data class Viewport(
    val viewWidth: Float,
    val viewHeight: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val zoom: Float = 1f,
    val left: Float = Float.NaN,
    val top: Float = Float.NaN,
) {
    /** Escala com a foto inteira visível. */
    val fitScale: Float get() = minOf(viewWidth / imageWidth, viewHeight / imageHeight)

    /** Pixels da tela por pixel da foto. */
    val scale: Float get() = fitScale * zoom

    fun toView(p: Point) = Point(p.x * scale + left, p.y * scale + top)

    fun toImage(x: Float, y: Float) = Point((x - left) / scale, (y - top) / scale)

    /** Amplia (ou reduz) por [factor] mantendo parado o ponto da tela ([cx], [cy]) entre os dedos. */
    fun zoomedAt(cx: Float, cy: Float, factor: Float): Viewport {
        val z = (zoom * factor).coerceIn(1f, MAX_ZOOM)
        val f = z / zoom
        return copy(zoom = z, left = cx - (cx - left) * f, top = cy - (cy - top) * f).clamped()
    }

    /** Arrasta a foto ampliada. */
    fun panned(dx: Float, dy: Float): Viewport = copy(left = left + dx, top = top + dy).clamped()

    /**
     * A área de exibição mudou de tamanho (ex.: cresceu ao ampliar): mantém o zoom e o ponto da
     * foto que estava no centro da área continua no centro.
     */
    fun resized(newWidth: Float, newHeight: Float): Viewport {
        val center = toImage(viewWidth / 2, viewHeight / 2)
        val next = Viewport(newWidth, newHeight, imageWidth, imageHeight, zoom = zoom, left = 0f, top = 0f)
        return next.copy(left = newWidth / 2 - center.x * next.scale, top = newHeight / 2 - center.y * next.scale).clamped()
    }

    /** Prende a foto: centralizada se couber, cobrindo a área toda se for maior. */
    fun clamped(): Viewport {
        val w = imageWidth * scale
        val h = imageHeight * scale
        fun place(pos: Float, size: Float, view: Float) =
            if (size <= view) (view - size) / 2 else (if (pos.isNaN()) (view - size) / 2 else pos).coerceIn(view - size, 0f)
        return copy(left = place(left, w, viewWidth), top = place(top, h, viewHeight))
    }

    companion object {
        const val MAX_ZOOM = 8f

        /** Foto inteira visível. */
        fun fit(viewWidth: Float, viewHeight: Float, imageWidth: Int, imageHeight: Int) =
            Viewport(viewWidth, viewHeight, imageWidth, imageHeight).clamped()
    }
}
