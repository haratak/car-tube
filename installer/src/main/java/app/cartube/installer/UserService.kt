package app.cartube.installer

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs in Shizuku's isolated process with adb shell privileges.
 */
class UserService : IUserService.Stub() {
    override fun destroy() {
        System.exit(0)
    }

    override fun exit() {
        destroy()
    }

    override fun installWithPlayStore(apkPath: String): String {
        val cmd = arrayOf(
            "pm",
            "install",
            "-i",
            PlayStoreInstall.PLAY_STORE,
            "--user",
            "0",
            "-r",
            apkPath,
        )
        return run(cmd)
    }

    override fun fixInstallSource(packageName: String): String {
        val cmd = arrayOf(
            "cmd",
            "package",
            "set-install-source",
            packageName,
            PlayStoreInstall.PACKAGE_INSTALLER,
            PlayStoreInstall.PLAY_STORE,
        )
        return run(cmd)
    }

    private fun run(cmd: Array<String>): String {
        val process = Runtime.getRuntime().exec(cmd)
        val stdout = process.inputStream.readTextSafe()
        val stderr = process.errorStream.readTextSafe()
        val code = process.waitFor()
        val text = buildString {
            if (stdout.isNotBlank()) append(stdout.trim())
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(stderr.trim())
            }
        }.ifBlank { "exit $code" }
        if (code != 0 || text.contains("Failure", ignoreCase = true)) {
            throw IllegalStateException(text)
        }
        return text
    }

    private fun java.io.InputStream.readTextSafe(): String =
        BufferedReader(InputStreamReader(this)).use { it.readText() }
}
