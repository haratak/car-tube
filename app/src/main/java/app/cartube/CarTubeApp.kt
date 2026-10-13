package app.cartube

import android.app.Application
import app.cartube.auto.PlaybackForegroundService

class CarTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlaybackStore.init(this)
        PlaybackForegroundService.ensureChannel(this)
    }
}
