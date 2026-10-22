package com.qalbidagx.pubg.system

import java.io.File

object ThermalReader {

    private val zones: List<Pair<String, File>> = runCatching {
        File("/sys/class/thermal").listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.mapNotNull { dir ->
                val name = runCatching { File(dir, "type").readText().trim() }.getOrNull()
                val temp = File(dir, "temp")
                if (name.isNullOrBlank() || !temp.exists()) null else name to temp
            }
            ?: emptyList()
    }.getOrDefault(emptyList())

    fun hottest(): Pair<String, Float>? = zones
        .mapNotNull { (name, file) ->
            val raw = runCatching { file.readText().trim().toLong() }.getOrNull()
                ?: return@mapNotNull null
            val celsius = if (raw > 1000L) raw / 1000f else raw.toFloat()
            if (celsius <= 0f || celsius > 120f) null else name to celsius
        }
        .maxByOrNull { it.second }

    fun zoneNames(): List<String> = zones.map { it.first }
}
