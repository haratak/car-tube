package app.cartube

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackTarget(val url: String, val generation: Long)

/**
 * Last page the phone asked the car screen to open. The car session and the
 * phone activity share a process, so a flow is enough; the URL is also saved
 * so it survives the process being killed before Android Auto binds.
 */
object PlaybackStore {
    private const val PREFS = "cartube"
    private const val KEY_URL = "target_url"
    private const val KEY_ACCEPTED = "safety_accepted"

    private val _target = MutableStateFlow(PlaybackTarget(YouTubeUrls.HOME, 0))
    val target: StateFlow<PlaybackTarget> = _target.asStateFlow()

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_URL, null)
        if (!saved.isNullOrBlank()) {
            _target.value = PlaybackTarget(saved, 0)
        }
    }

    fun open(url: String) {
        _target.value = PlaybackTarget(url, _target.value.generation + 1)
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_URL, url).apply()
        }
    }

    fun openHome() {
        open(YouTubeUrls.HOME)
    }

    fun isSafetyAccepted(): Boolean {
        return ::prefs.isInitialized && prefs.getBoolean(KEY_ACCEPTED, false)
    }

    fun acceptSafety() {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_ACCEPTED, true).apply()
        }
    }
}
