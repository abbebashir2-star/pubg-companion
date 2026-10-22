package com.qalbidagx.pubg.util

import android.util.Base64

data class SensValues(
    val camera: Float = 100f,
    val cameraAds: Float = 100f,
    val tpp: Float = 100f,
    val tppAds: Float = 100f,
    val fpp: Float = 100f,
    val fppAds: Float = 100f,
    val scope2x: Float = 100f,
    val scope3x: Float = 100f,
    val scope4x: Float = 100f,
    val scope6x: Float = 100f,
    val scope8x: Float = 100f,
    val freeLook: Float = 100f,
) {
    fun toList(): List<Float> = listOf(
        camera, cameraAds, tpp, tppAds, fpp, fppAds,
        scope2x, scope3x, scope4x, scope6x, scope8x, freeLook,
    )

    fun with(index: Int, value: Float): SensValues {
        val v = toList().toMutableList()
        v[index] = value
        return fromList(v)
    }

    companion object {
        val LABELS = listOf(
            "Camera", "Camera ADS", "TPP", "TPP ADS",
            "FPP", "FPP ADS", "2x", "3x", "4x", "6x", "8x", "Free look",
        )

        fun fromList(v: List<Float>) = SensValues(
            camera = v.getOrElse(0) { 100f },
            cameraAds = v.getOrElse(1) { 100f },
            tpp = v.getOrElse(2) { 100f },
            tppAds = v.getOrElse(3) { 100f },
            fpp = v.getOrElse(4) { 100f },
            fppAds = v.getOrElse(5) { 100f },
            scope2x = v.getOrElse(6) { 100f },
            scope3x = v.getOrElse(7) { 100f },
            scope4x = v.getOrElse(8) { 100f },
            scope6x = v.getOrElse(9) { 100f },
            scope8x = v.getOrElse(10) { 100f },
            freeLook = v.getOrElse(11) { 100f },
        )
    }
}

object SensitivityMath {

    const val MIN = 0f
    const val MAX = 300f

    fun convert(value: Float, fromDpi: Int, toDpi: Int): Float {
        if (fromDpi <= 0 || toDpi <= 0) return value
        return (value * fromDpi.toFloat() / toDpi.toFloat()).coerceIn(MIN, MAX)
    }

    fun convertAll(values: SensValues, fromDpi: Int, toDpi: Int): SensValues =
        SensValues.fromList(values.toList().map { convert(it, fromDpi, toDpi) })

    fun encode(values: SensValues, dpi: Int): String {
        val raw = (listOf(dpi) + values.toList().map { (it * 10f).toInt() }).joinToString(",")
        return "QDGX1-" + Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun decode(code: String): Pair<SensValues, Int>? {
        val body = code.trim().removePrefix("QDGX1-").removePrefix("PCS1-")
        val raw = runCatching {
            String(Base64.decode(body, Base64.NO_WRAP or Base64.URL_SAFE))
        }.getOrNull() ?: return null
        val nums = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (nums.size < 13) return null
        return SensValues.fromList(nums.drop(1).map { it / 10f }) to nums[0]
    }
}
