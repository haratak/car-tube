package app.cartube.auto

import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.cartube.PlaybackStore
import app.cartube.R
import kotlinx.coroutines.launch

/**
 * NavigationTemplate is the template that exposes a drawing surface. The PAN
 * action is what makes the host deliver taps and drags to [SurfaceCallback].
 * This screen does not start a navigation session.
 */
class CarTubeScreen(carContext: CarContext) : Screen(carContext) {
    private val renderer = CarSurfaceRenderer(carContext)
    private var safetyToastShown = false

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                renderer.release()
            }
        })
        lifecycleScope.launch {
            PlaybackStore.target.collect { target ->
                renderer.load(target.url)
            }
        }
        carContext.onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val view = renderer.webViewOrNull()
                    if (view != null && view.canGoBack()) {
                        view.goBack()
                    } else {
                        isEnabled = false
                        carContext.onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        if (!safetyToastShown) {
            safetyToastShown = true
            try {
                CarToast.makeText(carContext, R.string.car_safety_toast, CarToast.LENGTH_LONG).show()
            } catch (t: RuntimeException) {
                Log.w(TAG, "safety toast", t)
            }
        }
        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(action(R.drawable.ic_home, R.string.action_home) { PlaybackStore.openHome() })
                    .addAction(action(R.drawable.ic_refresh, R.string.action_reload) { renderer.reload() })
                    .build(),
            )
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.PAN)
                    .addAction(iconAction(R.drawable.ic_zoom_in) { renderer.zoomBy(ZOOM_IN) })
                    .addAction(iconAction(R.drawable.ic_zoom_out) { renderer.zoomBy(ZOOM_OUT) })
                    .build(),
            )
            .setPanModeListener { inPan ->
                Log.i(TAG, "panMode=$inPan")
            }
            .build()
    }

    private fun action(iconRes: Int, titleRes: Int, onClick: () -> Unit): Action {
        return Action.Builder()
            .setIcon(carIcon(iconRes))
            .setTitle(carContext.getString(titleRes))
            .setOnClickListener(onClick)
            .build()
    }

    private fun iconAction(iconRes: Int, onClick: () -> Unit): Action {
        return Action.Builder()
            .setIcon(carIcon(iconRes))
            .setOnClickListener(onClick)
            .build()
    }

    private fun carIcon(resId: Int): CarIcon {
        return CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
    }

    private companion object {
        const val TAG = "CarTube"
        const val ZOOM_IN = 1.2f
        const val ZOOM_OUT = 1f / 1.2f
    }
}
