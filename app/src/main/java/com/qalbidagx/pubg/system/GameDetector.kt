package com.qalbidagx.pubg.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.qalbidagx.pubg.core.GamePackages

class GameDetector(private val ctx: Context) {

    private val resumedEvent: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_RESUMED
        else UsageEvents.Event.MOVE_TO_FOREGROUND

    fun foregroundPackage(lookbackMs: Long = 20_000L): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - lookbackMs, end) ?: return null
        val e = UsageEvents.Event()
        var pkg: String? = null
        var newest = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == resumedEvent && e.timeStamp >= newest) {
                newest = e.timeStamp
                pkg = e.packageName
            }
        }
        return pkg
    }

    fun currentGamePackage(): String? {
        val fg = foregroundPackage() ?: return null
        return if (GamePackages.ALL.contains(fg)) fg else null
    }

    fun isGameInForeground(): Boolean = currentGamePackage() != null
}
