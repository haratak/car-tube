package app.cartube.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class CarTubeService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // Sideloaded installs talk to the Android Auto projection app and, in
        // development, the Desktop Head Unit. Unknown sources is already an
        // explicit user opt-in. See docs/ARCHITECTURE.md.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session = CarTubeSession()
}

class CarTubeSession : Session() {
    private var foregroundHooked = false

    override fun onCreateScreen(intent: Intent): Screen {
        if (!foregroundHooked) {
            foregroundHooked = true
            PlaybackForegroundService.start(carContext)
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    PlaybackForegroundService.stop(carContext)
                }
            })
        }
        return CarTubeScreen(carContext)
    }
}
