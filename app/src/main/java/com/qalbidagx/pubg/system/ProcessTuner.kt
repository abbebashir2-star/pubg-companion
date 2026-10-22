package com.qalbidagx.pubg.system

import com.qalbidagx.pubg.data.GameModeState

object ProcessTuner {

    private var lastReport = ""

    fun pids(pkg: String): List<Int> =
        ShizukuShell.exec("pidof $pkg")?.trim()
            ?.split(" ")?.mapNotNull { it.toIntOrNull() } ?: emptyList()

    fun apply(pkg: String, profile: DeviceProfile, pin: Boolean, boost: Boolean): List<String> {
        val out = mutableListOf<String>()
        val targets = pids(pkg)
        if (targets.isEmpty()) {
            out += "game process not found"
            return out
        }

        if (pin && profile.tasksetAvailable) {
            var hits = 0
            for (pid in targets) {
                if (ShizukuShell.ok("taskset -p ${profile.clusterMaskHex} $pid")) hits++
            }
            out += if (hits > 0) "pinned $hits to cores ${profile.bigCores}"
            else "taskset denied (CAP_SYS_NICE)"
        }

        if (boost) {
            val main = targets.first()
            out += if (ShizukuShell.ok("renice -n -10 -p $main")) "renice -10 ok" else "renice denied"
            if (profile.chrtAvailable) {
                out += if (ShizukuShell.ok("chrt -f -p 1 $main")) "fifo ok" else "chrt denied"
            }
        }

        return out
    }

    fun report(pkg: String): String {
        val targets = pids(pkg)
        if (targets.isEmpty()) return "not running"
        val main = targets.first()
        val mask = ShizukuShell.exec("taskset -p $main 2>/dev/null")
            ?.substringAfter("affinity list:", "?")?.trim()
        val nice = ShizukuShell.exec("cat /proc/$main/stat 2>/dev/null")
            ?.split(" ")?.getOrNull(18)?.trim()
        return "pid $main  affinity ${mask ?: "?"}  nice ${nice ?: "?"}"
    }

    fun noteIfChanged(pkg: String) {
        val r = report(pkg)
        if (r != lastReport) {
            lastReport = r
            GameModeState.note("Game process: $r")
        }
    }
}
