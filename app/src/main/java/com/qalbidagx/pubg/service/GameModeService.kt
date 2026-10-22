package com.qalbidagx.pubg.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.qalbidagx.pubg.core.PerfMode
import com.qalbidagx.pubg.data.*
import com.qalbidagx.pubg.system.*
import kotlinx.coroutines.*

class GameModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var detector: GameDetector
    private lateinit var repo: ProfileRepository

    private var activeProfile: Profile? = null
    private var device: DeviceProfile? = null
    private var snapshot: Snapshot? = null
    private var perfSnapshot: PerfTuner.Snapshot? = null
    private var thermalJob: Job? = null
    private var fpsJob: Job? = null
    private var pinJob: Job? = null
    private var clockJob: Job? = null
    private var thermalDowngraded = false

    private data class Snapshot(
        val brightnessRaw: Int,
        val autoBrightness: Boolean,
        val peakHz: Float,
        val minHz: Float,
    )

    override fun onCreate() {
        super.onCreate()
        detector = GameDetector(this)
        repo = ProfileRepository(AppDatabase.get(this).profiles())
        startForeground(
            NOTIF_ID,
            Notifier.build(this, Notifier.CH_GAME, "Waiting for PUBG", "No match running")
        )
        GameModeState.note("Service started")
        scope.launch {
            device = runCatching { DeviceProfiler.profile() }.getOrNull()
            device?.let {
                GameModeState.setDevice(it)
                GameModeState.note(
                    "Device: ${it.soc.ifBlank { "unknown" }}, ${it.cores} cores, " +
                        "big ${it.bigCores}, ${it.maxRefreshHz} Hz"
                )
            }
            loop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun loop() {
        while (isActive) {
            val inGame = detector.isGameInForeground()
            if (inGame && activeProfile == null) enterGameMode()
            else if (!inGame && activeProfile != null) exitGameMode()
            delay(POLL_MS)
        }
    }

    private suspend fun enterGameMode() {
        val profile = repo.enabled().firstOrNull() ?: return
        val dev = device ?: return
        val mode = PerfMode.fromId(profile.perfMode)
        val maxHz = if (dev.maxRefreshHz > 0) dev.maxRefreshHz else 120

        snapshot = Snapshot(
            brightnessRaw = BrightnessController.currentRaw(this),
            autoBrightness = BrightnessController.isAuto(this),
            peakHz = RefreshRateController.peak(this),
            minHz = RefreshRateController.min(this),
        )
        activeProfile = profile
        thermalDowngraded = false
        GameModeState.setInGame(true)
        GameModeState.setProfile(profile.name)
        GameModeState.setMode(mode)
        GameModeState.note("Match detected - applying \"${profile.name}\"")

        val tweaks = mutableListOf<Tweak>()
        perfSnapshot = if (ShizukuShell.hasPermission()) PerfTuner.snapshot(this, dev) else null

        if (profile.dnd) {
            val ok = DndController.setDnd(this, true)
            tweaks += Tweak("Do Not Disturb", ok, if (ok) "on" else "access denied")
        }

        val bright = BrightnessController.setManual(this, profile.brightnessPercent)
        tweaks += Tweak("Brightness", bright, if (bright) "${profile.brightnessPercent}%" else "denied")

        val hz = profile.refreshRateHz.coerceAtMost(maxHz)
        val rr = RefreshRateController.set(this, hz)
        tweaks += Tweak("Refresh rate", rr, if (rr) "$hz Hz locked" else "denied")

        if (ShizukuShell.hasPermission()) {
            tweaks += PerfTuner.apply(this, mode, dev)

            if (profile.chargeSeparation) {
                val (ok, detail) = ChargeController.setBypass(true)
                tweaks += Tweak("Charge separation", ok, detail)
            }

            val pkg = detector.currentGamePackage()
            if (pkg != null && (profile.pinToBigCores || profile.boostPriority)) {
                tweaks += Tweak(
                    "Process control", true,
                    ProcessTuner.apply(pkg, dev, profile.pinToBigCores, profile.boostPriority)
                        .joinToString("; ")
                )
            }

            if (profile.lowerResolution) {
                val ok = ResolutionController.apply(
                    profile.resolutionWidth, profile.resolutionHeight, profile.resolutionDpi
                )
                tweaks += Tweak(
                    "Resolution", ok,
                    if (ok) "${profile.resolutionWidth}x${profile.resolutionHeight}" else "failed"
                )
            }
        } else {
            tweaks += Tweak("Shizuku", false, "not granted - settings-only mode")
        }

        GameModeState.setTweaks(tweaks)
        tweaks.forEach {
            GameModeState.note("${if (it.ok) "OK  " else "FAIL"} ${it.name}: ${it.detail}")
        }

        GameModeState.setPlaytime(0L)
        if (profile.thermalGuard) startThermal(profile)
        startOverlay()
        startFpsProbe()
        startPinLoop(profile)
        startClock()
        updateNotification("Match running", "${profile.name} - ${mode.label}")
    }

    private suspend fun exitGameMode() {
        thermalJob?.cancel(); thermalJob = null
        fpsJob?.cancel(); fpsJob = null
        pinJob?.cancel(); pinJob = null
        clockJob?.cancel(); clockJob = null

        snapshot?.let {
            BrightnessController.restore(this, it.brightnessRaw, it.autoBrightness)
            RefreshRateController.restore(this, it.peakHz, it.minHz)
        }
        perfSnapshot?.let { perf -> device?.let { d -> PerfTuner.restore(this, perf, d) } }

        if (activeProfile?.chargeSeparation == true && ShizukuShell.hasPermission()) {
            ChargeController.setBypass(false)
        }
        if (activeProfile?.lowerResolution == true && ShizukuShell.hasPermission()) {
            ResolutionController.reset()
        }

        DndController.setDnd(this, false)
        stopService(Intent(this, OverlayService::class.java))

        GameModeState.note("Match ended - restored")
        activeProfile = null
        snapshot = null
        perfSnapshot = null
        GameModeState.setInGame(false)
        GameModeState.setProfile(null)
        GameModeState.setMode(null)
        updateNotification("Waiting for PUBG", "No match running")
    }

    private fun startThermal(profile: Profile) {
        thermalJob = scope.launch {
            while (isActive) {
                val hot = ThermalReader.hottest()
                if (hot != null) {
                    GameModeState.updateStats { it.copy(socTempC = hot.second) }
                    if (hot.second >= profile.temperatureLimitC && !thermalDowngraded) {
                        thermalDowngraded = true
                        RefreshRateController.set(this@GameModeService, 60)
                        GameModeState.note(
                            "%.1f C on %s - locked 60 Hz".format(hot.second, hot.first)
                        )
                    }
                }
                delay(2000L)
            }
        }
    }

    private fun startFpsProbe() {
        if (!ShizukuShell.hasPermission()) return
        fpsJob = scope.launch {
            while (isActive) {
                detector.currentGamePackage()?.let { pkg ->
                    SurfaceFlingerProbe.fps(pkg)?.let { fps ->
                        GameModeState.updateStats { it.copy(fps = fps, fpsIsReal = true) }
                    }
                }
                delay(1200L)
            }
        }
    }

    private fun startPinLoop(profile: Profile) {
        if (!profile.pinToBigCores) return
        pinJob = scope.launch {
            while (isActive) {
                delay(8000L)
                val dev = device ?: continue
                val pkg = detector.currentGamePackage() ?: continue
                ProcessTuner.apply(pkg, dev, true, false)
                ProcessTuner.noteIfChanged(pkg)
            }
        }
    }

    private fun startClock() {
        clockJob = scope.launch {
            while (isActive) {
                delay(1000L)
                GameModeState.setPlaytime(GameModeState.playtime.value + 1)
            }
        }
    }

    private fun startOverlay() {
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, Notifier.build(this, Notifier.CH_GAME, title, text))
    }

    override fun onDestroy() {
        scope.cancel()
        GameModeState.note("Service stopped")
        super.onDestroy()
    }

    private companion object {
        const val NOTIF_ID = 1001
        const val POLL_MS = 1500L
    }
}
