package com.qalbidagx.pubg.system

import java.io.File

data class SysSnapshot(
    val cpuLoadPercent: Int = 0,
    val cpuAvgMhz: Int = 0,
    val gpuMhz: Int = 0,
    val socTempC: Float = 0f,
    val batteryTempC: Float = 0f,
    val batteryCurrentMa: Int = 0,
)

class SysfsProbe {

    private var lastIdle = 0L
    private var lastTotal = 0L

    private val cpuFreqFiles: List<File> =
        (0 until 16).map { File("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq") }
            .filter { it.exists() }

    private val gpuFreqFiles: List<File> = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/kernel/gpu/gpu_clock",
    ).map(::File).filter { it.exists() }

    private val thermalFiles: List<File> = runCatching {
        File("/sys/class/thermal").listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.map { File(it, "temp") }
            ?.filter { it.exists() }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private val battTemp = File("/sys/class/power_supply/battery/temp")
    private val battCurrent = File("/sys/class/power_supply/battery/current_now")

    fun snapshot(): SysSnapshot {
        val freqs = cpuFreqFiles.mapNotNull { read(it) }
        val gpu = gpuFreqFiles.firstNotNullOfOrNull { read(it) } ?: 0L
        val socTemp = thermalFiles.mapNotNull { read(it) }
            .maxOrNull()?.let { it / 1000f } ?: 0f
        return SysSnapshot(
            cpuLoadPercent = cpuLoad(),
            cpuAvgMhz = if (freqs.isEmpty()) 0 else (freqs.average() / 1000).toInt(),
            gpuMhz = (gpu / 1_000_000L).toInt(),
            socTempC = socTemp,
            batteryTempC = (read(battTemp) ?: 0L) / 10f,
            batteryCurrentMa = ((read(battCurrent) ?: 0L) / 1000L).toInt(),
        )
    }

    private fun read(f: File): Long? =
        runCatching { f.readText().trim().toLong() }.getOrNull()

    private fun cpuLoad(): Int {
        val line = runCatching {
            File("/proc/stat").useLines { s -> s.firstOrNull { it.startsWith("cpu ") } }
        }.getOrNull() ?: return 0
        val p = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (p.size < 5) return 0
        val idle = p[3] + p[4]
        val total = p.sum()
        val dIdle = idle - lastIdle
        val dTotal = total - lastTotal
        lastIdle = idle
        lastTotal = total
        if (dTotal <= 0L) return 0
        return ((dTotal - dIdle) * 100 / dTotal).toInt().coerceIn(0, 100)
    }
}
