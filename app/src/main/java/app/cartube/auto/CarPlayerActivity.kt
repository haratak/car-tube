package app.cartube.auto

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.cartube.PlaybackStore
import app.cartube.R
import app.cartube.YouTubeChromeClient
import app.cartube.YouTubeWebView
import kotlinx.coroutines.launch

/**
 * Page Android Auto shows when it projects this app as a parked game.
 * Phones on Android 15 and newer launch [CAR_LAUNCHER] onto the head unit.
 * Hosts that bind [CarTubeService] keep drawing through the virtual display
 * on the template surface instead of this activity.
 */
class CarPlayerActivity : ComponentActivity() {
    private val main = Handler(Looper.getMainLooper())
    private var frame: FrameLayout? = null
    private var webView: WebView? = null
    private var customView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var crashes = 0
    private var foregroundHeld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parent = FrameLayout(this)
        parent.setBackgroundColor(Color.BLACK)
        frame = parent
        setContentView(parent)
        attachPage()
        if (!foregroundHeld) {
            foregroundHeld = true
            PlaybackForegroundService.acquire(this)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackStore.target.collect { target ->
                    val view = webView ?: return@collect
                    YouTubeWebView.load(view, target.url)
                }
            }
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val view = webView
                    if (view != null && view.canGoBack()) {
                        view.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.resumeTimers()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (foregroundHeld) {
            foregroundHeld = false
            PlaybackForegroundService.release(this)
        }
        destroyPage()
        super.onDestroy()
    }

    private fun attachPage() {
        val parent = frame ?: return
        val web = WebView(this)
        parent.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        YouTubeWebView.configure(web) { main.post { rebuildAfterCrash() } }
        web.webChromeClient = object : YouTubeChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                fullscreenCallback = callback
                web.visibility = View.GONE
                parent.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }

            override fun onHideCustomView() {
                val shown = customView ?: return
                parent.removeView(shown)
                customView = null
                web.visibility = View.VISIBLE
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
            }
        }
        web.requestFocus()
        web.onResume()
        web.resumeTimers()
        webView = web
    }

    private fun rebuildAfterCrash() {
        crashes += 1
        val url = PlaybackStore.target.value.url
        destroyPage()
        if (crashes > MAX_CRASHES || isDestroyed) {
            if (!isDestroyed) {
                Toast.makeText(this, R.string.car_render_failed, Toast.LENGTH_LONG).show()
            }
            return
        }
        attachPage()
        val view = webView ?: return
        YouTubeWebView.load(view, url)
    }

    private fun destroyPage() {
        val shown = customView
        customView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        shown?.let { (it.parent as? FrameLayout)?.removeView(it) }
        val view = webView
        webView = null
        view?.apply {
            stopLoading()
            onPause()
            webChromeClient = null
            webViewClient = android.webkit.WebViewClient()
            (parent as? FrameLayout)?.removeView(this)
            destroy()
        }
    }

    private companion object {
        const val MAX_CRASHES = 2
    }
}
