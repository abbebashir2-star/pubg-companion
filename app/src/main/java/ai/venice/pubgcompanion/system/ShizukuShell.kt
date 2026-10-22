package ai.venice.pubgcompanion.system

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuShell {

    fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = binderAlive() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(requestCode: Int = 4242) {
        if (binderAlive() && !hasPermission()) {
            runCatching { Shizuku.requestPermission(requestCode) }
        }
    }

    fun exec(command: String): String? = runCatching {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val out = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        out.trim()
    }.getOrNull()
}

object ResolutionController {

    fun apply(width: Int, height: Int, dpi: Int): Boolean =
        ShizukuShell.exec("wm size ${width}x$height && wm density $dpi") != null

    fun reset(): Boolean = ShizukuShell.exec("wm size reset && wm density reset") != null
}

object BackgroundKiller {

    val PRESETS = listOf(
        "com.facebook.katana",
        "com.facebook.orca",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.apps.maps",
        "com.samsung.android.app.spage",
    )

    fun kill(packages: List<String> = PRESETS): Int =
        packages.count { ShizukuShell.exec("am force-stop $it") != null }
}

object SurfaceFlingerProbe {

    private fun layerFor(pkg: String): String? =
        ShizukuShell.exec("dumpsys SurfaceFlinger --list")
            ?.lines()
            ?.firstOrNull { it.contains(pkg, true) && it.contains("SurfaceView", true) }

    fun fps(pkg: String): Int? {
        val layer = layerFor(pkg) ?: return null
        val out = ShizukuShell.exec("dumpsys SurfaceFlinger --latency \"$layer\"") ?: return null
        val stamps = out.lines().drop(1)
            .mapNotNull { it.trim().split(" ").getOrNull(1)?.toLongOrNull() }
            .filter { it > 0L }
        if (stamps.size < 4) return null
        val span = (stamps.max() - stamps.min()) / 1_000_000_000.0
        if (span <= 0.0) return null
        return (stamps.size / span).toInt()
    }
}
