package com.qalbidagx.pubg.data

data class CrosshairStyle(
    val shape: String = "crossdot",
    val color: Int = 0xFFFF2A3C.toInt(),
    val sizePx: Int = 22,
    val thicknessPx: Int = 3,
    val gapPx: Int = 8,
    val dotRadiusPx: Int = 3,
    val alpha: Int = 220,
    val outline: Boolean = true,
) {
    companion object {
        val SHAPES = listOf(
            "cross" to "Cross",
            "crossdot" to "Cross + dot",
            "dot" to "Dot",
            "circle" to "Circle",
            "chevron" to "Chevron",
            "tee" to "T",
        )

        val COLORS = listOf(
            0xFFFF2A3C.toInt() to "Red",
            0xFF00E5FF.toInt() to "Cyan",
            0xFF3DDC84.toInt() to "Green",
            0xFFFFB300.toInt() to "Amber",
            0xFFFFFFFF.toInt() to "White",
            0xFFFF00FF.toInt() to "Magenta",
            0xFF9C27B0.toInt() to "Purple",
            0xFF000000.toInt() to "Black",
        )
    }
}
