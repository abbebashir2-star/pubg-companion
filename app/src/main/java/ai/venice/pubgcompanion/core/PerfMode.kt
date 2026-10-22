package ai.venice.pubgcompanion.core

import androidx.compose.ui.graphics.Color

enum class PerfMode(
    val id: String,
    val label: String,
    val blurb: String,
    val accent: Color,
    val cpuGovernor: String?,
    val gpuGovernor: String?,
    val coresOnline: Int,
    val animationScale: Float,
    val fixedPerformance: Boolean,
    val thermalOverride: Boolean,
    val touchBoost: Boolean,
    val enhancedCpu: Boolean,
) {
    ECO(
        "eco", "ECO", "Cold and quiet",
        Color(0xFF3DDC84),
        "schedutil", null, 4, 1.0f, false, false, false, false,
    ),
    BALANCED(
        "balanced", "BALANCED", "Daily default",
        Color(0xFF00E5FF),
        "schedutil", null, 6, 1.0f, false, false, true, true,
    ),
    PERFORMANCE(
        "performance", "PERFORMANCE", "All cores, snappy UI",
        Color(0xFFFFB300),
        "performance", "performance", 8, 0.5f, false, false, true, true,
    ),
    RISE(
        "rise", "RISE", "Max output, no throttle",
        Color(0xFFFF2A3C),
        "performance", "performance", 8, 0.0f, true, true, true, true,
    );

    companion object {
        fun fromId(id: String): PerfMode = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}
