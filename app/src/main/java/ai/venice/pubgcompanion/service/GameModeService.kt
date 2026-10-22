package ai.venice.pubgcompanion.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import ai.venice.pubgcompanion.core.GamePackages
import ai.venice.pubgcompanion.core.PerfMode
import ai.venice.pubgcompanion.data.*
import ai.venice.pubgcompanion.system.*
import kotlinx.coroutines.*

class GameModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var detector: GameDetector
    private lateinit var repo: ProfileRepository

    private var thermal: ThermalMonitor? = null
    private var activeProfile: Profile? = null
    private var snapshot: Snapshot? = null
    private var perfSnapshot: PerfTuner.Snapshot? = null
    private var thermalDowngraded = false
    private var fpsJob: Job? = null

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
        GameModeState.note("Game mode service started")
        scope.launch { loop() }
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
        val mode = PerfMode.fromId(profile.perfMode)

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
        GameModeState.note("Match started - applying \"${profile.name}\"")

        val cores = if (ShizukuShell.hasPermission()) PerfTuner.totalCores()
        else Runtime.getRuntime().availableProcessors()
        perfSnapshot = if (ShizukuShell.hasPermission()) PerfTuner.snapshot(this, cores) else null

        if (profile.dnd) {
            if (DndController.setDnd(this, true)) GameModeState.note("Do Not Disturb on")
            else GameModeState.note("Do Not Disturb denied")
        }
        if (BrightnessController.setManual(this, profile.brightnessPercent)) {
            GameModeState.note("Brightness ${profile.brightnessPercent}%")
        }
        if (RefreshRateController.set(this, profile.refreshRateHz)) {
            GameModeState.note("Refresh rate locked to ${profile.refreshRateHz} Hz")
        }

        if (ShizukuShell.hasPermission()) {
            PerfTuner.apply(this, mode, cores).forEach { GameModeState.note(it) }
        } else {
            GameModeState.note("No Shizuku - settings-only tweaks")
            if (mode.touchBoost) {
                Settings.System.putInt(contentResolver, "touch_sensitivity", 1)
            }
        }

        if (profile.lowerResolution) {
            if (ShizukuShell.hasPermission()) {
                val ok = ResolutionController.apply(
                    profile.resolutionWidth, profile.resolutionHeight, profile.resolutionDpi
                )
                GameModeState.note(
                    if (ok) "Resolution ${profile.resolutionWidth}x${profile.resolutionHeight}"
                    else "Resolution change failed"
                )
            } else GameModeState.note("Resolution unchanged (no Shizuku)")
        }

        if (profile.killBackground && ShizukuShell.hasPermission()) {
            GameModeState.note("Stopped ${BackgroundKiller.kill()} background apps")
        }

        if (profile.thermalGuard) startThermal(profile)
        startOverlay()
        startFpsProbe()
        updateNotification("Match running", "${profile.name} - ${mode.label}")
    }

    private suspend fun exitGameMode() {
        thermal?.stop(); thermal = null
        fpsJob?.cancel(); fpsJob = null

        snapshot?.let {
            BrightnessController.restore(this, it.brightnessRaw, it.autoBrightness)
            RefreshRateController.restore(this, it.peakHz, it.minHz)
        }
        perfSnapshot?.let { PerfTuner.restore(this, it) }

        if (activeProfile?.lowerResolution == true && ShizukuShell.hasPermission()) {
            ResolutionController.reset()
            GameModeState.note("Resolution restored")
        }

        DndController.setDnd(this, false)
        stopService(Intent(this, OverlayService::class.java))

        GameModeState.note("Match ended - system restored")
        activeProfile = null
        snapshot = null
        perfSnapshot = null
        GameModeState.setInGame(false)
        GameModeState.setProfile(null)
        GameModeState.setMode(null)
        updateNotification("Waiting for PUBG", "No match running")
    }

    private fun startThermal(profile: Profile) {
        thermal = ThermalMonitor(this) { temp, throttling ->
            GameModeState.updateStats { it.copy(batteryTempC = temp, throttling = throttling) }
            if (throttling && temp >= profile.temperatureLimitC && !thermalDowngraded) {
                thermalDowngraded = true
                RefreshRateController.set(this, 60)
                GameModeState.note("Thermal throttle at %.1f C - locking 60 Hz".format(temp))
            }
        }.also { it.start() }
    }

    private fun startFpsProbe() {
        if (!ShizukuShell.hasPermission()) {
            GameModeState.note("No Shizuku - FPS shown as estimate")
            return
        }
        val pkg = detector.currentGamePackage() ?: GamePackages.PRIMARY
        fpsJob = scope.launch {
            while (isActive) {
                SurfaceFlingerProbe.fps(pkg)?.let { fps ->
                    GameModeState.updateStats { it.copy(fps = fps, fpsIsReal = true) }
                }
                delay(1200L)
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
        thermal?.stop()
        scope.cancel()
        GameModeState.note("Game mode service stopped")
        super.onDestroy()
    }

    private companion object {
        const val NOTIF_ID = 1001
        const val POLL_MS = 1500L
    }
}
