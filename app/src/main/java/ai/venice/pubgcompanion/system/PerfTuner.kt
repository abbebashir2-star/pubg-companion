package ai.venice.pubgcompanion.system

import android.content.Context
import android.provider.Settings
import ai.venice.pubgcompanion.core.PerfMode

object PerfTuner {

    data class Snapshot(
        val windowAnim: Float,
        val transitionAnim: Float,
        val animatorAnim: Float,
        val touchSensitivity: Int,
        val screenTimeout: Int,
        val enhancedCpu: Int?,
        val fixedPerformance: Boolean?,
        val thermalStatus: Int?,
        val governors: Map<Int, String>,
    )

    private fun cpuPath(core: Int) = "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor"

    private val GPU_PATHS = listOf(
        "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
        "/sys/kernel/gpu/gpu_governor",
        "/sys/devices/platform/soc/soc:gpu/devfreq/governor",
    )

    fun totalCores(): Int =
        ShizukuShell.exec("ls -d /sys/devices/system/cpu/cpu[0-9]*")
            ?.lines()?.count { it.isNotBlank() }
            ?: Runtime.getRuntime().availableProcessors()

    private fun read(path: String): String? =
        ShizukuShell.exec("cat $path 2>/dev/null")?.trim()?.ifBlank { null }

    private fun write(path: String, value: String): Boolean =
        ShizukuShell.exec("echo $value > $path 2>/dev/null && echo ok")?.contains("ok") == true

    private fun gpuPath(): String? =
        GPU_PATHS.firstOrNull { ShizukuShell.exec("test -e $it && echo y") == "y" }

    private fun putGlobal(key: String, value: Any) {
        ShizukuShell.exec("settings put global $key $value")
    }

    fun snapshot(ctx: Context, cores: Int): Snapshot = Snapshot(
        windowAnim = Settings.Global.getFloat(ctx.contentResolver, "window_animation_scale", 1f),
        transitionAnim = Settings.Global.getFloat(ctx.contentResolver, "transition_animation_scale", 1f),
        animatorAnim = Settings.Global.getFloat(ctx.contentResolver, "animator_duration_scale", 1f),
        touchSensitivity = Settings.System.getInt(ctx.contentResolver, "touch_sensitivity", 0),
        screenTimeout = Settings.System.getInt(
            ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000
        ),
        enhancedCpu = ShizukuShell.exec("settings get global sem_enhanced_cpu_responsiveness")
            ?.trim()?.toIntOrNull(),
        fixedPerformance = ShizukuShell.exec("cmd power get-fixed-performance-mode-enabled")
            ?.contains("true"),
        thermalStatus = ShizukuShell.exec("cmd thermalservice get-current-thermal-status")
            ?.trim()?.toIntOrNull(),
        governors = (0 until cores).associateWith { read(cpuPath(it)) ?: "?" },
    )

    fun apply(ctx: Context, mode: PerfMode, cores: Int): List<String> {
        val out = mutableListOf<String>()

        putGlobal("window_animation_scale", mode.animationScale)
        putGlobal("transition_animation_scale", mode.animationScale)
        putGlobal("animator_duration_scale", mode.animationScale)
        out += "Animation scale ${mode.animationScale}x"

        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 10 * 60_000
        )
        out += "Screen timeout 10 min"

        if (mode.touchBoost) {
            Settings.System.putInt(ctx.contentResolver, "touch_sensitivity", 1)
            out += "Touch sensitivity on"
        }

        if (mode.enhancedCpu) {
            putGlobal("sem_enhanced_cpu_responsiveness", 1)
            out += "Enhanced CPU responsiveness on"
        }

        mode.cpuGovernor?.let { gov ->
            val hits = (0 until cores).count { write(cpuPath(it), gov) }
            out += if (hits > 0) "CPU governor $gov on $hits/$cores cores"
            else "CPU governor locked by kernel"
        }

        mode.gpuGovernor?.let { gov ->
            gpuPath()?.let { if (write(it, gov)) out += "GPU governor $gov" }
        }

        if (mode.fixedPerformance && ShizukuShell.hasPermission()) {
            ShizukuShell.exec("cmd power set-fixed-performance-mode-enabled true")
            out += "Fixed performance mode on"
        }

        if (mode.thermalOverride && ShizukuShell.hasPermission()) {
            ShizukuShell.exec("cmd thermalservice override-status 0")
            out += "Thermal throttling disabled"
        }

        return out
    }

    fun restore(ctx: Context, snap: Snapshot) {
        putGlobal("window_animation_scale", snap.windowAnim)
        putGlobal("transition_animation_scale", snap.transitionAnim)
        putGlobal("animator_duration_scale", snap.animatorAnim)
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, snap.screenTimeout
        )
        Settings.System.putInt(ctx.contentResolver, "touch_sensitivity", snap.touchSensitivity)
        snap.enhancedCpu?.let { putGlobal("sem_enhanced_cpu_responsiveness", it) }
        snap.governors.forEach { (core, gov) -> if (gov != "?") write(cpuPath(core), gov) }
        snap.fixedPerformance?.let {
            ShizukuShell.exec("cmd power set-fixed-performance-mode-enabled $it")
        }
        snap.thermalStatus?.let {
            ShizukuShell.exec("cmd thermalservice override-status $it")
        }
    }

    fun aotCompile(pkg: String): String =
        ShizukuShell.exec("cmd package compile -m speed -f $pkg")?.takeIf { it.isNotBlank() }
            ?: "Compile finished"
}
