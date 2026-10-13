package app.cartube

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder

sealed class ResolveResult {
    data class Navigate(val url: String) : ResolveResult()
    data object NotYouTube : ResolveResult()
}

/**
 * Turns whatever the user typed into a YouTube page this app is willing to open.
 * Anything that is not YouTube is rejected so the car screen is not a general browser.
 */
object YouTubeUrls {
    const val HOME = "https://m.youtube.com/"
    const val EMBED_REFERER = "https://www.youtube.com/"

    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")

    fun resolve(input: String): ResolveResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ResolveResult.Navigate(HOME)
        if (videoId.matches(trimmed)) return ResolveResult.Navigate(embed(trimmed))
        if (trimmed.any { it.isWhitespace() }) return ResolveResult.Navigate(search(trimmed))

        val explicitUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        val candidate = when {
            explicitUrl -> trimmed
            isSchemelessYouTube(trimmed) -> "https://$trimmed"
            else -> null
        }
        if (candidate == null) return ResolveResult.Navigate(search(trimmed))

        val uri = try {
            URI(candidate)
        } catch (e: URISyntaxException) {
            return if (explicitUrl) ResolveResult.NotYouTube else ResolveResult.Navigate(search(trimmed))
        }
        val host = uri.host
        if (host.isNullOrBlank() || !isYouTubeHost(host)) return ResolveResult.NotYouTube
        val id = extractVideoId(uri)
        if (id != null) return ResolveResult.Navigate(embed(id))
        return ResolveResult.Navigate(forceHttps(candidate))
    }

    fun isAllowedNavigation(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return false
        }
        val host = uri.host ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false
        if (isYouTubeHost(host)) return true
        return scheme == "https" && host.equals("accounts.google.com", ignoreCase = true)
    }

    fun embed(id: String): String {
        return "https://www.youtube.com/embed/$id" +
            "?autoplay=1&playsinline=1&rel=0&modestbranding=1&enablejsapi=1" +
            "&origin=https%3A%2F%2Fwww.youtube.com"
    }

    fun search(query: String): String {
        val encoded = URLEncoder.encode(query.trim().take(MAX_QUERY), Charsets.UTF_8.name())
        return "https://m.youtube.com/results?search_query=$encoded"
    }

    private fun isSchemelessYouTube(value: String): Boolean {
        return value.startsWith("youtu.be/") ||
            value.startsWith("youtube.com/") ||
            value.startsWith("m.youtube.com/") ||
            value.startsWith("www.youtube.com/") ||
            value.startsWith("music.youtube.com/") ||
            value.startsWith("youtube-nocookie.com/") ||
            value.startsWith("www.youtube-nocookie.com/")
    }

    private fun forceHttps(url: String): String {
        return if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
    }

    private fun extractVideoId(uri: URI): String? {
        val host = canonicalHost(uri.host ?: return null)
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (host == "youtu.be") {
            return segments.firstOrNull()?.let(::decode)?.takeIf { videoId.matches(it) }
        }
        queryParam(uri, "v")?.takeIf { videoId.matches(it) }?.let { return it }
        val markers = setOf("embed", "shorts", "live", "v", "e")
        for (index in segments.indices) {
            if (segments[index] in markers && index + 1 <= segments.lastIndex) {
                val id = decode(segments[index + 1])
                if (videoId.matches(id)) return id
            }
        }
        return null
    }

    private fun queryParam(uri: URI, name: String): String? {
        val query = uri.rawQuery ?: return null
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            val rawKey = if (separator >= 0) part.substring(0, separator) else part
            val rawValue = if (separator >= 0) part.substring(separator + 1) else ""
            if (decode(rawKey) == name) return decode(rawValue)
        }
        return null
    }

    private fun decode(value: String): String {
        return try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (e: IllegalArgumentException) {
            value
        }
    }

    internal fun isYouTubeHost(host: String): Boolean {
        val normalized = canonicalHost(host)
        return normalized == "youtu.be" ||
            normalized == "youtube.com" ||
            normalized.endsWith(".youtube.com") ||
            normalized == "youtube-nocookie.com" ||
            normalized.endsWith(".youtube-nocookie.com")
    }

    private fun canonicalHost(host: String): String = host.lowercase().removePrefix("www.")

    private const val MAX_QUERY = 500
}
