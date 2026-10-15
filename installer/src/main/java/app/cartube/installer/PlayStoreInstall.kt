package app.cartube.installer

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Installs an APK through Shizuku so PackageManager records
 * [PLAY_STORE] as the installer of record (`pm install -i`).
 *
 * Clean-room helper for sideloaded CarTube builds. Not derived from KingInstaller (GPL).
 */
object PlayStoreInstall {
    private const val TAG = "CarTubeInstaller"
    const val PLAY_STORE = "com.android.vending"
    const val PACKAGE_INSTALLER = "com.google.android.packageinstaller"

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            "app.cartube.installer",
            UserService::class.java.name,
        ),
    )
        .daemon(false)
        .processNameSuffix("install_service")
        .version(1)

    fun shizukuReady(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun install(apk: File): Result<String> {
        if (!Shizuku.pingBinder()) {
            return Result.failure(IllegalStateException("Shizuku が起動していません。"))
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(IllegalStateException("Shizuku の権限がまだありません。"))
        }
        if (!apk.isFile) {
            return Result.failure(IllegalArgumentException("APK が見つかりません。"))
        }
        return withUserService { service ->
            service.installWithPlayStore(apk.absolutePath)
        }
    }

    fun fixInstallSource(packageName: String): Result<String> {
        return withUserService { service ->
            service.fixInstallSource(packageName)
        }.onFailure {
            Log.i(TAG, "set-install-source unavailable: ${it.message}")
        }
    }

    private fun withUserService(block: (IUserService) -> String): Result<String> {
        val serviceRef = AtomicReference<IUserService>()
        val errorRef = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceRef.set(IUserService.Stub.asInterface(binder))
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceRef.set(null)
            }
        }
        return try {
            Shizuku.bindUserService(userServiceArgs, connection)
            if (!latch.await(15, TimeUnit.SECONDS)) {
                return Result.failure(IllegalStateException("Shizuku UserService の接続がタイムアウトしました。"))
            }
            val service = serviceRef.get()
                ?: return Result.failure(IllegalStateException("Shizuku UserService が利用できません。"))
            Result.success(block(service))
        } catch (t: Throwable) {
            errorRef.set(t)
            Result.failure(t)
        } finally {
            try {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
            } catch (t: Throwable) {
                Log.w(TAG, "unbindUserService failed", t)
            }
        }
    }
}
