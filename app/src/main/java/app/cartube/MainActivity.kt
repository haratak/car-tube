package app.cartube

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.car.app.connection.CarConnection
import androidx.core.content.ContextCompat
import app.cartube.auto.PlaybackForegroundService
import app.cartube.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var projected = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The car surface does not depend on the phone notification. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openButton.setOnClickListener { submitInput() }
        binding.homeButton.setOnClickListener { openHome() }
        binding.batteryButton.setOnClickListener { openBatterySettings() }
        binding.settingsButton.setOnClickListener { openAutostartSettings() }
        binding.urlEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                submitInput()
                true
            } else {
                false
            }
        }

        CarConnection(this).type.observe(this) { type ->
            projected = type == CarConnection.CONNECTION_TYPE_PROJECTION ||
                type == CarConnection.CONNECTION_TYPE_NATIVE
            renderStatus()
        }
        renderStatus()

        if (PlaybackStore.isSafetyAccepted()) {
            ensureNotificationPermission()
        } else {
            showSafetyDialog()
        }
    }

    private fun renderStatus() {
        binding.connectionStatus.text = when {
            !isAndroidAutoInstalled() -> getString(R.string.status_missing)
            projected -> getString(R.string.status_connected)
            else -> getString(R.string.status_disconnected)
        }
    }

    private fun showSafetyDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.safety_title)
            .setMessage(R.string.safety_message)
            .setCancelable(false)
            .setPositiveButton(R.string.safety_accept) { _, _ ->
                PlaybackStore.acceptSafety()
                ensureNotificationPermission()
            }
            .show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun submitInput() {
        when (val result = YouTubeUrls.resolve(binding.urlEdit.text?.toString().orEmpty())) {
            is ResolveResult.Navigate -> {
                binding.urlInputLayout.error = null
                PlaybackStore.open(result.url)
                PlaybackForegroundService.start(this)
                val message = if (projected) R.string.sent_connected else R.string.sent_saved
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
            ResolveResult.NotYouTube -> {
                binding.urlInputLayout.error = getString(R.string.invalid_url)
            }
        }
    }

    private fun openHome() {
        binding.urlInputLayout.error = null
        PlaybackStore.openHome()
        PlaybackForegroundService.start(this)
        val message = if (projected) R.string.sent_connected else R.string.sent_saved
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun openBatterySettings() {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(request)
        } catch (e: ActivityNotFoundException) {
            startOrExplain(appDetailsIntent())
        } catch (e: SecurityException) {
            startOrExplain(appDetailsIntent())
        }
    }

    private fun openAutostartSettings() {
        val miui = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
        }
        try {
            startActivity(miui)
        } catch (e: ActivityNotFoundException) {
            startOrExplain(appDetailsIntent())
        } catch (e: SecurityException) {
            startOrExplain(appDetailsIntent())
        }
    }

    private fun startOrExplain(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.settings_unavailable, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun appDetailsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }

    private fun isAndroidAutoInstalled(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(ANDROID_AUTO_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
    }
}
