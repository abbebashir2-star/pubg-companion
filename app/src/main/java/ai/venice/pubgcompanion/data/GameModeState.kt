package ai.venice.pubgcompanion.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ai.venice.pubgcompanion.core.PerfMode
import java.time.LocalTime

data class LiveStats(
    val fps: Int = 0,
    val fpsIsReal: Boolean = false,
    val batteryTempC: Float = 0f,
    val throttling: Boolean = false,
    val pingMs: Int = -1,
    val jitterMs: Float = 0f,
    val lossPercent: Float = 0f,
)

object GameModeState {

    private val _inGame = MutableStateFlow(false)
    val inGame: StateFlow<Boolean> = _inGame

    private val _activeProfile = MutableStateFlow<String?>(null)
    val activeProfile: StateFlow<String?> = _activeProfile

    private val _mode = MutableStateFlow<PerfMode?>(null)
    val mode: StateFlow<PerfMode?> = _mode

    private val _stats = MutableStateFlow(LiveStats())
    val stats: StateFlow<LiveStats> = _stats

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    fun setInGame(v: Boolean) { _inGame.value = v }
    fun setProfile(v: String?) { _activeProfile.value = v }
    fun setMode(m: PerfMode?) { _mode.value = m }
    fun updateStats(block: (LiveStats) -> LiveStats) { _stats.value = block(_stats.value) }

    fun note(msg: String) {
        val stamp = LocalTime.now().withNano(0).toString()
        _log.value = (_log.value + "$stamp  $msg").takeLast(60)
    }
}
