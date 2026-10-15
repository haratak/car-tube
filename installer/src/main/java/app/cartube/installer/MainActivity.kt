package app.cartube.installer

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import app.cartube.installer.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var stagedApk: File? = null
    private var permissionRequestCode = REQUEST_SHIZUKU

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        stageApk(uri)
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != permissionRequestCode) return@OnRequestPermissionResultListener
            refreshStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.shizuku_granted, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        binding.pickApk.setOnClickListener { pickApk.launch(arrayOf("application/vnd.android.package-archive")) }
        binding.requestShizuku.setOnClickListener { requestShizuku() }
        binding.install.setOnClickListener { installStaged() }
        binding.refreshDiag.setOnClickListener { refreshDiagnostics() }
        refreshStatus()
        refreshDiagnostics()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, R.string.shizuku_not_running, Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refreshStatus()
            return
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            Toast.makeText(this, R.string.shizuku_rationale, Toast.LENGTH_LONG).show()
        }
        Shizuku.requestPermission(permissionRequestCode++)
    }

    private fun stageApk(uri: Uri) {
        val out = File(getExternalFilesDir(null), "staged.apk")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: run {
            Toast.makeText(this, R.string.apk_read_failed, Toast.LENGTH_LONG).show()
            return
        }
        stagedApk = out
        binding.apkPath.text = getString(R.string.apk_selected, out.name)
        binding.install.isEnabled = PlayStoreInstall.shizukuReady()
    }

    private fun installStaged() {
        val apk = stagedApk
        if (apk == null) {
            Toast.makeText(this, R.string.pick_apk_first, Toast.LENGTH_SHORT).show()
            return
        }
        binding.install.isEnabled = false
        binding.log.text = getString(R.string.install_running)
        Thread {
            val result = PlayStoreInstall.install(apk)
            val fix = result.mapCatching {
                PlayStoreInstall.fixInstallSource(CARTUBE_PACKAGE).getOrDefault(it)
            }
            runOnUiThread {
                binding.install.isEnabled = PlayStoreInstall.shizukuReady()
                binding.log.text = fix.fold(
                    onSuccess = { getString(R.string.install_ok, it) },
                    onFailure = { getString(R.string.install_failed, it.message ?: it.toString()) },
                )
                refreshDiagnostics()
            }
        }.start()
    }

    private fun refreshStatus() {
        val running = Shizuku.pingBinder()
        val granted = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        binding.shizukuStatus.text = when {
            !running -> getString(R.string.shizuku_not_running)
            granted -> getString(R.string.shizuku_ready)
            else -> getString(R.string.shizuku_need_permission)
        }
        binding.requestShizuku.isVisible = !granted
        binding.install.isEnabled = granted && stagedApk != null
    }

    private fun refreshDiagnostics() {
        val pm = packageManager
        val installed = try {
            pm.getPackageInfo(CARTUBE_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!installed) {
            binding.diagnostics.text = getString(R.string.cartube_not_installed)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = pm.getInstallSourceInfo(CARTUBE_PACKAGE)
            binding.diagnostics.text = getString(
                R.string.install_source,
                info.installingPackageName ?: "—",
                info.initiatingPackageName ?: "—",
                info.originatingPackageName ?: "—",
            )
        } else {
            @Suppress("DEPRECATION")
            val installer = pm.getInstallerPackageName(CARTUBE_PACKAGE) ?: "—"
            binding.diagnostics.text = getString(R.string.install_source_legacy, installer)
        }
    }

    private companion object {
        const val REQUEST_SHIZUKU = 1001
        const val CARTUBE_PACKAGE = "app.cartube"
    }
}
