package app.cartube

import android.graphics.Color
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Settings shared by the host-surface presentation and the parked activity.
 * YouTube refuses the embed player when the user agent still looks like a
 * WebView, and the embed endpoint returns player error 153 without a Referer.
 */
object YouTubeWebView {
    private const val TAG = "CarTube"

    fun configure(web: WebView, onRenderProcessGone: () -> Unit) {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.setBackgroundColor(Color.BLACK)
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = UserAgents.forYouTube(userAgentString ?: "")
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url ?: return true
                val allowed = YouTubeUrls.isAllowedNavigation(uri.toString())
                if (!allowed) Log.i(TAG, "Blocked navigation to ${uri.host}")
                return !allowed
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.e(TAG, "WebView renderer gone didCrash=${detail.didCrash()}")
                onRenderProcessGone()
                return true
            }
        }
    }

    fun load(web: WebView, url: String) {
        web.loadUrl(url, mapOf("Referer" to YouTubeUrls.EMBED_REFERER))
    }
}

/** Cancels JS dialogs. A car surface has no activity window that can show them. */
open class YouTubeChromeClient : WebChromeClient() {
    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.cancel()
        return true
    }

    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?,
    ): Boolean {
        result?.cancel()
        return true
    }
}
