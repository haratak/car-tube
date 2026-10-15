package app.cartube.auto

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import app.cartube.PlaybackStore
import app.cartube.R
import app.cartube.YouTubeChromeClient
import app.cartube.YouTubeWebView

/**
 * Renders the YouTube WebView into the surface Android Auto hands the app.
 * The platform sample for custom map drawing does the same thing: a private
 * virtual display whose output surface is the car surface, plus a
 * [Presentation] so ordinary views (here, a WebView) composite onto it.
 */
class CarSurfaceRenderer(
    private val carContext: CarContext,
) : SurfaceCallback {
    private val main = Handler(Looper.getMainLooper())
    private val touch = TouchForwarder(
        target = { customView ?: webView },
        surfaceSize = { surfaceWidth to surfaceHeight },
    )

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    private var boundSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarPresentation? = null
    private var webView: WebView? = null
    private var customView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var released = false
    private var crashes = 0

    fun webViewOrNull(): WebView? = webView

    fun load(url: String) {
        if (released) return
        val view = webView ?: return
        Log.i(TAG, "Loading ${url.substringBefore('?')}")
        YouTubeWebView.load(view, url)
    }

    fun reload() {
        webView?.reload()
    }

    fun zoomBy(factor: Float) {
        val view = webView ?: return
        if (!factor.isFinite() || factor <= 0f) return
        try {
            view.zoomBy(factor)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "zoomBy failed", e)
        }
    }

    fun release() {
        released = true
        touch.cancel()
        teardownDisplay()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface
        val width = surfaceContainer.width
        val height = surfaceContainer.height
        if (surface == null || width <= 0 || height <= 0) {
            Log.e(TAG, "Surface not usable w=$width h=$height")
            toast(R.string.car_surface_failed)
            return
        }
        if (surface === boundSurface && width == surfaceWidth && height == surfaceHeight && webView != null) {
            return
        }
        teardownDisplay()
        val dpi = surfaceContainer.dpi.takeIf { it > 0 } ?: DEFAULT_DPI
        Log.i(TAG, "Surface ${width}x$height dpi=$dpi")
        val displayManager = carContext.applicationContext.getSystemService(DisplayManager::class.java)
        if (displayManager == null) {
            Log.e(TAG, "DisplayManager missing")
            toast(R.string.car_surface_failed)
            return
        }
        // Flag 0 matches the platform sample in "Draw maps". The display is
        // private to this app; the car host composites the surface we were given.
        val virtual = try {
            @Suppress("DEPRECATION")
            displayManager.createVirtualDisplay(
                DISPLAY_NAME,
                width,
                height,
                dpi,
                surface,
                0,
            )
        } catch (t: RuntimeException) {
            Log.e(TAG, "createVirtualDisplay failed", t)
            toast(R.string.car_surface_failed)
            return
        }
        if (virtual == null) {
            Log.e(TAG, "createVirtualDisplay returned null")
            toast(R.string.car_surface_failed)
            return
        }
        val pres = CarPresentation(carContext.applicationContext, virtual.display)
        try {
            pres.show()
        } catch (t: RuntimeException) {
            Log.e(TAG, "Presentation.show failed", t)
            virtual.release()
            toast(R.string.car_surface_failed)
            return
        }
        boundSurface = surface
        surfaceWidth = width
        surfaceHeight = height
        surfaceDpi = dpi
        virtualDisplay = virtual
        presentation = pres
        webView = pres.page
        pres.page.onResume()
        pres.page.resumeTimers()
        crashes = 0
        load(PlaybackStore.target.value.url)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        teardownDisplay()
    }

    override fun onClick(x: Float, y: Float) {
        touch.click(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        touch.scroll(distanceX, distanceY)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (!touch.endDrag()) {
            webView?.flingScroll(velocityX.toInt(), velocityY.toInt())
        }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (focusX.isNaN() || focusY.isNaN()) return
        zoomBy(scaleFactor)
    }

    private fun recreateAfterCrash() {
        if (released) return
        crashes += 1
        val surface = boundSurface
        val width = surfaceWidth
        val height = surfaceHeight
        val dpi = surfaceDpi
        teardownDisplay()
        if (crashes > MAX_CRASHES || surface == null || !surface.isValid || width <= 0 || height <= 0) {
            toast(R.string.car_render_failed)
            return
        }
        val container = SurfaceContainer(surface, width, height, dpi)
        onSurfaceAvailable(container)
    }

    private fun teardownDisplay() {
        touch.cancel()
        customView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        val view = webView
        val pres = presentation
        val display = virtualDisplay
        webView = null
        presentation = null
        virtualDisplay = null
        boundSurface = null
        surfaceWidth = 0
        surfaceHeight = 0
        surfaceDpi = DEFAULT_DPI
        view?.apply {
            stopLoading()
            onPause()
            webChromeClient = null
            webViewClient = WebViewClient()
        }
        try {
            pres?.dismiss()
        } catch (t: RuntimeException) {
            Log.w(TAG, "dismiss presentation", t)
        }
        try {
            view?.destroy()
        } catch (t: RuntimeException) {
            Log.w(TAG, "destroy webview", t)
        }
        try {
            display?.release()
        } catch (t: RuntimeException) {
            Log.w(TAG, "release display", t)
        }
    }

    private fun configureWebView(web: WebView) {
        YouTubeWebView.configure(web) { main.post { recreateAfterCrash() } }
        web.webChromeClient = object : YouTubeChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                fullscreenCallback = callback
                web.visibility = View.GONE
                (web.parent as? FrameLayout)?.addView(
                    view,
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
                )
            }

            override fun onHideCustomView() {
                val shown = customView ?: return
                (shown.parent as? FrameLayout)?.removeView(shown)
                customView = null
                web.visibility = View.VISIBLE
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
            }
        }
        web.requestFocus()
    }

    private fun toast(resId: Int) {
        try {
            CarToast.makeText(carContext, resId, CarToast.LENGTH_LONG).show()
        } catch (t: RuntimeException) {
            Log.w(TAG, "toast failed", t)
        }
    }

    private inner class CarPresentation(
        outer: Context,
        display: Display,
    ) : Presentation(outer, display, R.style.Theme_CarTube_Presentation) {
        lateinit var page: WebView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val web = WebView(context)
            val frame = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
            configureWebView(web)
            page = web
            setContentView(frame)
        }
    }

    private companion object {
        const val TAG = "CarTube"
        const val DISPLAY_NAME = "cartube"
        const val DEFAULT_DPI = 160
        const val MAX_CRASHES = 2
    }
}
