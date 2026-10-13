package app.cartube.auto

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import app.cartube.SurfacePoints

/**
 * Android Auto does not deliver a raw touch stream. It reports taps, drags,
 * and flings in surface pixels. Those are replayed as finger events on the
 * WebView, which lives in this process on the virtual display.
 *
 * Positive [scroll] distances follow the car host: X grows to the right and
 * Y grows downward, matching a finger drag in that direction.
 */
class TouchForwarder(
    private val target: () -> View?,
    private val surfaceSize: () -> Pair<Int, Int>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var dragging = false
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private val endDragRunnable = Runnable { endDrag() }

    fun click(x: Float, y: Float, posted: Boolean = false) {
        val view = target() ?: return
        if (view.width <= 0 || view.height <= 0) {
            if (!posted) view.post { click(x, y, posted = true) }
            return
        }
        if (dragging) endDrag()
        val (surfaceW, surfaceH) = surfaceSize()
        val (px, py) = SurfacePoints.map(x, y, surfaceW, surfaceH, view.width, view.height)
        val now = SystemClock.uptimeMillis()
        dispatch(view, MotionEvent.ACTION_DOWN, px, py, now, now)
        dispatch(view, MotionEvent.ACTION_UP, px, py, now, now + TAP_UP_DELAY_MS)
    }

    fun scroll(distanceX: Float, distanceY: Float) {
        val view = target() ?: return
        if (view.width <= 0 || view.height <= 0) return
        val (surfaceW, surfaceH) = surfaceSize()
        val dx = SurfacePoints.scaleDelta(distanceX, surfaceW, view.width)
        val dy = SurfacePoints.scaleDelta(distanceY, surfaceH, view.height)
        val now = SystemClock.uptimeMillis()
        if (!dragging) {
            dragging = true
            downTime = now
            lastX = view.width / 2f
            lastY = view.height / 2f
            dispatch(view, MotionEvent.ACTION_DOWN, lastX, lastY, downTime, now)
        }
        lastX = (lastX + dx).coerceIn(0f, (view.width - 1).toFloat())
        lastY = (lastY + dy).coerceIn(0f, (view.height - 1).toFloat())
        dispatch(view, MotionEvent.ACTION_MOVE, lastX, lastY, downTime, now)
        handler.removeCallbacks(endDragRunnable)
        handler.postDelayed(endDragRunnable, SCROLL_IDLE_MS)
    }

    /** Finishes an in-progress drag. Returns false when there was nothing to finish. */
    fun endDrag(): Boolean {
        if (!dragging) return false
        handler.removeCallbacks(endDragRunnable)
        val view = target()
        dragging = false
        if (view != null) {
            dispatch(view, MotionEvent.ACTION_UP, lastX, lastY, downTime, SystemClock.uptimeMillis())
        }
        return true
    }

    fun cancel() {
        handler.removeCallbacks(endDragRunnable)
        if (!dragging) return
        val view = target()
        dragging = false
        if (view != null) {
            dispatch(view, MotionEvent.ACTION_CANCEL, lastX, lastY, downTime, SystemClock.uptimeMillis())
        }
    }

    private fun dispatch(view: View, action: Int, x: Float, y: Float, down: Long, time: Long) {
        val event = obtain(action, x, y, down, time)
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun obtain(action: Int, x: Float, y: Float, down: Long, time: Long): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            down,
            time,
            action,
            1,
            arrayOf(properties),
            arrayOf(coords),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }

    private companion object {
        const val TAP_UP_DELAY_MS = 40L
        const val SCROLL_IDLE_MS = 200L
    }
}
