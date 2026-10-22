package com.qalbidagx.pubg.system

import java.io.File

data class DeviceProfile(
    val soc: String,
    val board: String,
    val gpuPath: String?,
    val cores: Int,
    val bigCores: List<Int>,
    val maxRefreshHz: Int,
    val samsung: Boolean,
    val thermalZones: Int,
    val tasksetAvailable: Boolean,
    val chrtAvailable: Boolean,
) {
    val clusterMaskHex: String
        get() = java.lang.Long.toHexString(
            bigCores.fold(0L) { acc, c -> acc or (1L shl c) }
        )
}

object DeviceProfiler {

    private fun prop(name: String) = ShizukuShell.exec("getprop $name")?.trim().orEmpty()

    private fun readLong(path: String): Long =
        ShizukuShell.exec("cat $path 2>/dev/null")?.trim()?.toLongOrNull() ?: 0L

    private fun coreIndexes(): List<Int> =
        ShizukuShell.exec("ls -d /sys/devices/system/cpu/cpu[0-9]* 2>/dev/null")
            ?.lines()
            ?.mapNotNull { it.substringAfterLast("cpu").toIntOrNull() }
            ?.sorted()
            ?: (0 until Runtime.getRuntime().availableProcessors()).toList()

    private fun gpuPath(): String? = listOf(
        "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
        "/sys/kernel/gpu/gpu_governor",
        "/sys/devices/platform/soc/soc:gpu/devfreq/governor",
        "/sys/devices/platform/*.mali/devfreq/governor",
    ).firstOrNull { ShizukuShell.exec("test -e $it && echo y") == "y" }

    private fun maxRefresh(): Int {
        val out = ShizukuShell.exec(
            "dumpsys display 2>/dev/null | grep -oE 'fps=[0-9]+' | sed 's/fps=//' | sort -n | tail -1"
        )
        return out?.trim()?.toIntOrNull()?.coerceIn(60, 240) ?: 60
    }

    private fun has(bin: String) = ShizukuShell.ok("command -v $bin")

    fun profile(): DeviceProfile {
        val all = coreIndexes()
        val freqs = all.associateWith {
            readLong("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")
        }
        val top = freqs.values.maxOrNull() ?: 0L
        var big = if (top <= 0L) emptyList()
        else freqs.filter { it.value == top }.keys.sorted()
        if (big.isEmpty()) big = all.takeLast((all.size / 2).coerceAtLeast(1)).sorted()
        return DeviceProfile(
            soc = prop("ro.soc.model")
                .ifBlank { prop("ro.board.platform") }
                .ifBlank { prop("ro.hardware") },
            board = prop("ro.product.board"),
            gpuPath = gpuPath(),
            cores = all.size,
            bigCores = big,
            maxRefreshHz = maxRefresh(),
            samsung = prop("ro.product.manufacturer").contains("samsung", true),
            thermalZones = runCatching {
                File("/sys/class/thermal").listFiles()
                    ?.count { it.name.startsWith("thermal_zone") } ?: 0
            }.getOrDefault(0),
            tasksetAvailable = has("taskset"),
            chrtAvailable = has("chrt"),
        )
    }
}
