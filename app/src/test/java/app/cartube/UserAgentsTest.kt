package app.cartube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserAgentsTest {
    @Test
    fun stripsWebViewMarkersAndKeepsChromeVersion() {
        val raw = "Mozilla/5.0 (Linux; Android 14; 24030PN60G Build/UKQ1.231003.002; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"
        val sanitized = UserAgents.forYouTube(raw)
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; 24030PN60G Build/UKQ1.231003.002) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36",
            sanitized,
        )
        assertFalse(sanitized.contains("wv"))
        assertFalse(sanitized.contains("Version/4.0"))
    }
}
