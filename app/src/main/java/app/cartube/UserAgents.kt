package app.cartube

object UserAgents {
    /**
     * YouTube refuses the embedded player when the UA advertises a WebView
     * (`wv` and `Version/4.0`). Stripping those tokens leaves the device's
     * real Chrome version in place.
     */
    fun forYouTube(webViewUa: String): String {
        return webViewUa.replace("; wv", "").replace("Version/4.0 ", "")
    }
}
