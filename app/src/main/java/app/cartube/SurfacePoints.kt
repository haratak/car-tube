package app.cartube

object SurfacePoints {
    fun map(
        x: Float,
        y: Float,
        surfaceW: Int,
        surfaceH: Int,
        viewW: Int,
        viewH: Int,
    ): Pair<Float, Float> {
        if (surfaceW <= 0 || surfaceH <= 0 || viewW <= 0 || viewH <= 0) return x to y
        val mappedX = x * viewW / surfaceW
        val mappedY = y * viewH / surfaceH
        return mappedX.coerceIn(0f, (viewW - 1).coerceAtLeast(0).toFloat()) to
            mappedY.coerceIn(0f, (viewH - 1).coerceAtLeast(0).toFloat())
    }

    fun scaleDelta(distance: Float, surface: Int, view: Int): Float {
        if (surface <= 0 || view <= 0) return distance
        return distance * view / surface
    }
}
