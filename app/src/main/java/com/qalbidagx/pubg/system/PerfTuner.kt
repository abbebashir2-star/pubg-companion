package com.qalbidagx.pubg.system

import android.content.Context
import android.provider.Settings
import com.qalbidagx.pubg.core.PerfMode

data class Tweak(val name: String, val ok: Boolean, val detail: String)

object PerfTuner {

    val RESTRICT = listOf(
        "com.facebook.katana",
        "com.facebook.system",
        "com.facebook.appmanager",
        "com.facebook.services",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.apps.maps",
        "com.samsung.android.app.spage",
        "com.samsung.android.arzone",
        "com.google.android.apps.tachyon",
        "com.google.android.videos",
        "com.google.android.apps.docs",
    )

    val FORCE_STOP = listOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.apps.maps",
        "com.samsung.android.app.spage",
    )

    data class Snapshot(
        val windowAnim: Float,
        val transitionAnim: Float,
        val animatorAnim: Float,
        val touchSensitivity: Int,
        val screenTimeout: Int,
        val enhancedCpu: Int?,
        val wifiScan: Int?,
        val fixedPerformance: Boolean?,
        val thermalStatus: Int?,
        val governors: Map<Int, String>,
        val gpuGovernor: String?,
    )

    private fun read(path: String) =
        ShizukuShell.exec("cat $path 2>/dev/null")?.trim()?.ifBlank { null }

    private fun write(path: String, value: String): Boolean =
        ShizukuShell.ok("echo $value > $path")

    private fun putGlobal(key: String, value: Any) {
        ShizukuShell.exec("settings put global $key $value")
    }

    private fun globalInt(key: String): Int? =
        ShizukuShell.exec("settings get global $key")?.trim()?.toIntOrNull()

    private fun govPath(core: Int) = "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor"

    fun snapshot(ctx: Context, device: DeviceProfile): Snapshot = Snapshot(
        windowAnim = Settings.Global.getFloat(ctx.contentResolver, "window_animation_scale", 1f),
        transitionAnim = Settings.Global.getFloat(ctx.contentResolver, "transition_animation_scale", 1f),
        animatorAnim = Settings.Global.getFloat(ctx.contentResolver, "animator_duration_scale", 1f),
        touchSensitivity = Settings.System.getInt(ctx.contentResolver, "touch_sensitivity", 0),
        screenTimeout = Settings.System.getInt(
            ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000
        ),
        enhancedCpu = globalInt("sem_enhanced_cpu_responsiveness"),
        wifiScan = globalInt("wifi_scan_always_enabled"),
        fixedPerformance = ShizukuShell.exec("cmd power get-fixed-performance-mode-enabled")
            ?.contains("true"),
        thermalStatus = ShizukuShell.exec("cmd thermalservice get-current-thermal-status")
            ?.trim()?.toIntOrNull(),
        governors = (0 until device.cores).associateWith { read(govPath(it)) ?: "?" },
        gpuGovernor = device.gpuPath?.let { read(it) },
    )

    fun apply(ctx: Context, mode: PerfMode, device: DeviceProfile): List<Tweak> {
        val r = mutableListOf<Tweak>()

        mode.cpuGovernor?.let { gov ->
            val hits = (0 until device.cores).count { write(govPath(it), gov) }
            r += if (hits == 0) Tweak("CPU governor", false, "kernel locked, needs root")
            else Tweak("CPU governor", true, "$gov on $hits/${device.cores} cores")
        }

        mode.gpuGovernor?.let { gov ->
            val path = device.gpuPath
            if (path == null) r += Tweak("GPU governor", false, "no devfreq node")
            else {
                val ok = write(path, gov)
                r += Tweak("GPU governor", ok, if (ok) gov else "write denied")
            }
        }

        if (mode.fixedPerformance) {
            val ok = ShizukuShell.ok("cmd power set-fixed-performance-mode-enabled true")
            r += Tweak("Fixed performance mode", ok, if (ok) "enabled" else "unsupported")
        }

        if (mode.thermalOverride) {
            val ok = ShizukuShell.ok("cmd thermalservice override-status 0")
            r += Tweak("Thermal override", ok, if (ok) "throttling disabled" else "unsupported")
        }

        putGlobal("window_animation_scale", mode.animationScale)
        putGlobal("transition_animation_scale", mode.animationScale)
        putGlobal("animator_duration_scale", mode.animationScale)
        r += Tweak("Animation scales", true, "${mode.animationScale}x")

        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 10 * 60_000
        )
        r += Tweak("Screen timeout", true, "10 min")

        if (mode.touchBoost) {
            Settings.System.putInt(ctx.contentResolver, "touch_sensitivity", 1)
            val v = Settings.System.getInt(ctx.contentResolver, "touch_sensitivity", 0) == 1
            r += Tweak("Touch sensitivity", v, if (v) "on" else "key missing")
        }

        if (mode.enhancedCpu && device.samsung) {
            putGlobal("sem_enhanced_cpu_responsiveness", 1)
            r += Tweak(
                "Enhanced CPU response",
                globalInt("sem_enhanced_cpu_responsiveness") == 1,
                "Samsung key"
            )
        }

        putGlobal("wifi_scan_always_enabled", 0)
        r += Tweak("Background wifi scanning", true, "off")

        var restricted = 0
        for (p in RESTRICT) {
            if (ShizukuShell.ok("cmd appops set $p RUN_IN_BACKGROUND ignore")) restricted++
            ShizukuShell.exec("cmd appops set $p RUN_ANY_IN_BACKGROUND ignore")
            ShizukuShell.exec("cmd activity set-inactive $p true")
        }
        r += Tweak("Background apps restricted", restricted > 0, "$restricted packages")

        val killed = FORCE_STOP.count { ShizukuShell.ok("am force-stop $it") }
        r += Tweak("Force stopped", true, "$killed apps")

        ShizukuShell.exec("pm trim-caches 500G")
        r += Tweak("Cache trimmed", true, "done")

        return r
    }

    fun restore(ctx: Context, snap: Snapshot, device: DeviceProfile) {
        putGlobal("window_animation_scale", snap.windowAnim)
        putGlobal("transition_animation_scale", snap.transitionAnim)
        putGlobal("animator_duration_scale", snap.animatorAnim)
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, snap.screenTimeout
        )
        Settings.System.putInt(ctx.contentResolver, "touch_sensitivity", snap.touchSensitivity)
        snap.enhancedCpu?.let { putGlobal("sem_enhanced_cpu_responsiveness", it) }
        snap.wifiScan?.let { putGlobal("wifi_scan_always_enabled", it) }
        snap.governors.forEach { (core, gov) -> if (gov != "?") write(govPath(core), gov) }
        if (device.gpuPath != null && snap.gpuGovernor != null) {
            write(device.gpuPath, snap.gpuGovernor)
        }
        snap.fixedPerformance?.let {
            ShizukuShell.exec("cmd power set-fixed-performance-mode-enabled $it")
        }
        snap.thermalStatus?.let {
            ShizukuShell.exec("cmd thermalservice override-status $it")
        }
        for (p in RESTRICT) {
            ShizukuShell.exec("cmd appops set $p RUN_IN_BACKGROUND allow")
            ShizukuShell.exec("cmd activity set-inactive $p false")
        }
    }

    fun aotCompile(pkg: String): Tweak {
        val out = ShizukuShell.exec("cmd package compile -m speed -f $pkg")
        return Tweak("AOT compile", true, out?.take(60) ?: "done")
    }

    fun dexopt(): Tweak {
        ShizukuShell.exec("cmd package bg-dexopt-job")
        return Tweak("Dexopt", true, "queued")
    }
}
