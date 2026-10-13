package app.cartube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlsTest {
    @Test
    fun emptyOpensMobileHome() {
        val result = YouTubeUrls.resolve("  ") as ResolveResult.Navigate
        assertEquals(YouTubeUrls.HOME, result.url)
    }

    @Test
    fun rawIdBecomesEmbed() {
        val result = YouTubeUrls.resolve("dQw4w9WgXcQ") as ResolveResult.Navigate
        assertTrue(result.url.startsWith("https://www.youtube.com/embed/dQw4w9WgXcQ?"))
        assertTrue(result.url.contains("autoplay=1"))
        assertTrue(result.url.contains("playsinline=1"))
    }

    @Test
    fun watchUrlBecomesEmbed() {
        val result = YouTubeUrls.resolve(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=43s",
        ) as ResolveResult.Navigate
        assertTrue(result.url.contains("/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun shortLinkAndShortsAndLive() {
        assertTrue(url("https://youtu.be/dQw4w9WgXcQ?t=43").contains("/embed/dQw4w9WgXcQ"))
        assertTrue(url("youtu.be/dQw4w9WgXcQ").contains("/embed/dQw4w9WgXcQ"))
        assertTrue(url("https://www.youtube.com/shorts/dQw4w9WgXcQ").contains("/embed/dQw4w9WgXcQ"))
        assertTrue(url("https://www.youtube.com/live/dQw4w9WgXcQ").contains("/embed/dQw4w9WgXcQ"))
        assertTrue(url("https://music.youtube.com/watch?v=dQw4w9WgXcQ").contains("/embed/dQw4w9WgXcQ"))
        assertTrue(url("https://m.youtube.com/watch?v=dQw4w9WgXcQ").contains("/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun playlistPassesThroughOverHttps() {
        val result = url("http://www.youtube.com/playlist?list=PL123")
        assertEquals("https://www.youtube.com/playlist?list=PL123", result)
    }

    @Test
    fun keywordBecomesMobileSearch() {
        val result = url("猫 ピアノ")
        assertTrue(result.startsWith("https://m.youtube.com/results?search_query="))
        assertFalse(result.contains(" "))
        assertTrue(result.contains("%E7%8C%AB"))
    }

    @Test
    fun shortTokenIsASearchNotAnId() {
        val result = url("abcdefghij")
        assertTrue(result.contains("search_query=abcdefghij"))
    }

    @Test
    fun rejectsOtherSitesEvenIfTheyLookLikeAVideo() {
        assertEquals(
            ResolveResult.NotYouTube,
            YouTubeUrls.resolve("https://example.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            ResolveResult.NotYouTube,
            YouTubeUrls.resolve("https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun navigationAllowlist() {
        assertTrue(YouTubeUrls.isAllowedNavigation("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YouTubeUrls.isAllowedNavigation("https://accounts.google.com/ServiceLogin"))
        assertFalse(YouTubeUrls.isAllowedNavigation("https://example.com/"))
        assertFalse(YouTubeUrls.isAllowedNavigation("javascript:alert(1)"))
        assertFalse(YouTubeUrls.isAllowedNavigation("http://accounts.google.com/"))
    }

    private fun url(input: String): String = (YouTubeUrls.resolve(input) as ResolveResult.Navigate).url
}
