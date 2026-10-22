package com.qalbidagx.pubg.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.qalbidagx.pubg.core.PerfMode
import com.qalbidagx.pubg.system.DeviceProfile
import com.qalbidagx.pubg.system.SysSnapshot
import com.qalbidagx.pubg.system.Tweak
import java.time.LocalTime

data class LiveStats(
    val fps: Int = 0,
    val fpsIsReal: Boolean = false,
    val cpuLoad: Int = 0,
    val cpuMhz: Int = 0,
    val gpuMhz: Int = 0,
    val socTempC: Float = 0f,
    val batteryTempC: Float = 0f,
    val batteryCurrentMa: Int = 0,
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

    private val _device = MutableStateFlow<DeviceProfile?>(null)
    val device: StateFlow<DeviceProfile?> = _device

    private val _tweaks = MutableStateFlow<List<Tweak>>(emptyList())
    val tweaks: StateFlow<List<Tweak>> = _tweaks

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    private val _playtime = MutableStateFlow(0L)
    val playtime: StateFlow<Long> = _playtime

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    fun setInGame(v: Boolean) { _inGame.value = v }
    fun setProfile(v: String?) { _activeProfile.value = v }
    fun setMode(m: PerfMode?) { _mode.value = m }
    fun setDevice(d: DeviceProfile?) { _device.value = d }
    fun setTweaks(t: List<Tweak>) { _tweaks.value = t }
    fun setRecording(v: Boolean) { _recording.value = v }
    fun setPlaytime(seconds: Long) { _playtime.value = seconds }
    fun updateStats(block: (LiveStats) -> LiveStats) { _stats.value = block(_stats.value) }

    fun applySys(s: SysSnapshot) {
        _stats.value = _stats.value.copy(
            cpuLoad = s.cpuLoadPercent,
            cpuAvgMhz = s.cpuAvgMhz,
            gpuMhz = s.gpuMhz,
            socTempC = s.socTempC,
            batteryTempC = s.batteryTempC,
            batteryCurrentMa = s.batteryCurrentMa,
        )
    }

    fun note(msg: String) {
        val stamp = LocalTime.now().withNano(0).toString()
        _log.value = (_log.value + "$stamp  $msg").takeLast(80)
    }
}
