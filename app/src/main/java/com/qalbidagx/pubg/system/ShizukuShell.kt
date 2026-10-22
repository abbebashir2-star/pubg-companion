package com.qalbidagx.pubg.system

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
        val err = process.errorStream.bufferedReader().use { it.readText() }
        process.waitFor()
        (out + err).trim()
    }.getOrNull()

    fun ok(command: String): Boolean =
        exec("$command 2>&1 && echo __OK__")?.contains("__OK__") == true
}

object ResolutionController {

    fun apply(width: Int, height: Int, dpi: Int): Boolean =
        ShizukuShell.ok("wm size ${width}x$height && wm density $dpi")

    fun reset(): Boolean = ShizukuShell.ok("wm size reset && wm density reset")
}

object ChargeController {

    private fun currentMa(): Int? =
        runCatching {
            java.io.File("/sys/class/power_supply/battery/current_now")
                .readText().trim().toLong()
        }.getOrNull()?.let { (it / 1000L).toInt() }

    fun setBypass(on: Boolean): Pair<Boolean, String> {
        val before = currentMa()
        val ok = if (on) ShizukuShell.ok("dumpsys battery set ac 0 && dumpsys battery set usb 0")
        else ShizukuShell.ok("dumpsys battery reset")
        Thread.sleep(1200)
        val after = currentMa()
        val detail = when {
            before == null || after == null -> "current unreadable"
            on && after < before -> "current ${before} -> ${after} mA"
            !on -> "charger restored"
            else -> "no change (${before} mA)"
        }
        return ok to detail
    }
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
