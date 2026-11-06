package com.davicolzer.zureader.vision

/** Retângulo em pixels; [right] e [bottom] são exclusivos. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int, val area: Int = (right - left) * (bottom - top)) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val size: Int get() = maxOf(width, height)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** `true` se [other] está dentro desta caixa (com 2 px de folga). */
    fun contains(other: PixelBox): Boolean =
        other.left >= left - 2 && other.right <= right + 2 && other.top >= top - 2 && other.bottom <= bottom + 2

    fun union(other: PixelBox) = PixelBox(
        minOf(left, other.left), minOf(top, other.top),
        maxOf(right, other.right), maxOf(bottom, other.bottom),
        area + other.area,
    )
}

/** Manchas de tinta conectadas (vizinhança de 8). */
object Components {
    fun find(mask: BooleanArray, width: Int, height: Int): List<PixelBox> {
        require(mask.size == width * height)
        val visited = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        val boxes = mutableListOf<PixelBox>()
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var top = 0
            stack[top++] = start
            visited[start] = true
            var minX = width; var minY = height; var maxX = -1; var maxY = -1; var area = 0
            while (top > 0) {
                val i = stack[--top]
                val x = i % width
                val y = i / width
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width || (dx == 0 && dy == 0)) continue
                        val n = ny * width + nx
                        if (mask[n] && !visited[n]) {
                            visited[n] = true
                            stack[top++] = n
                        }
                    }
                }
            }
            boxes += PixelBox(minX, minY, maxX + 1, maxY + 1, area)
        }
        return boxes
    }
}
