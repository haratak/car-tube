package app.cartube

import org.junit.Assert.assertEquals
import org.junit.Test

class SurfacePointsTest {
    @Test
    fun identityWhenSizesMatch() {
        val (x, y) = SurfacePoints.map(10f, 20f, 100, 200, 100, 200)
        assertEquals(10f, x, 0.01f)
        assertEquals(20f, y, 0.01f)
    }

    @Test
    fun scalesToTheWebView() {
        val (x, y) = SurfacePoints.map(10f, 20f, 100, 100, 200, 400)
        assertEquals(20f, x, 0.01f)
        assertEquals(80f, y, 0.01f)
    }

    @Test
    fun clampsInsideTheView() {
        val (x, y) = SurfacePoints.map(-5f, 500f, 100, 100, 100, 100)
        assertEquals(0f, x, 0.01f)
        assertEquals(99f, y, 0.01f)
    }

    @Test
    fun zeroSurfaceKeepsTheInput() {
        val (x, y) = SurfacePoints.map(3f, 4f, 0, 0, 100, 100)
        assertEquals(3f, x, 0.01f)
        assertEquals(4f, y, 0.01f)
    }

    @Test
    fun scrollDeltaScalesWithTheView() {
        assertEquals(20f, SurfacePoints.scaleDelta(10f, 100, 200), 0.01f)
        assertEquals(10f, SurfacePoints.scaleDelta(10f, 0, 200), 0.01f)
    }
}
