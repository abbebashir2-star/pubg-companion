package ai.venice.pubgcompanion.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.*
import kotlin.math.sqrt

object DndController {
    fun setDnd(ctx: Context, on: Boolean): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return true
    }
}

object BrightnessController {

    fun currentRaw(ctx: Context): Int =
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)

    fun isAuto(ctx: Context): Boolean =
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun setManual(ctx: Context, percent: Int): Boolean {
        if (!Settings.System.canWrite(ctx)) return false
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
            (percent.coerceIn(1, 100) * 255) / 100
        )
        return true
    }

    fun restore(ctx: Context, raw: Int, auto: Boolean) {
        if (!Settings.System.canWrite(ctx)) return
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
    }
}

object RefreshRateController {

    private const val KEY_PEAK = "peak_refresh_rate"
    private const val KEY_MIN = "min_refresh_rate"

    fun peak(ctx: Context): Float = Settings.System.getFloat(ctx.contentResolver, KEY_PEAK, 0f)
    fun min(ctx: Context): Float = Settings.System.getFloat(ctx.contentResolver, KEY_MIN, 0f)

    fun set(ctx: Context, hz: Int): Boolean {
        if (!Settings.System.canWrite(ctx)) return false
        Settings.System.putFloat(ctx.contentResolver, KEY_PEAK, hz.toFloat())
        Settings.System.putFloat(ctx.contentResolver, KEY_MIN, hz.toFloat())
        return true
    }

    fun restore(ctx: Context, peakHz: Float, minHz: Float) {
        if (!Settings.System.canWrite(ctx)) return
        Settings.System.putFloat(ctx.contentResolver, KEY_PEAK, peakHz)
        Settings.System.putFloat(ctx.contentResolver, KEY_MIN, minHz)
    }
}

class ThermalMonitor(
    private val ctx: Context,
    private val onReading: (tempC: Float, throttling: Boolean) -> Unit,
) {

    private var receiver: BroadcastReceiver? = null
    private var lastTemp = 0f
    private val power: PowerManager by lazy {
        ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private val statusListener = PowerManager.OnThermalStatusChangedListener { status ->
        onReading(lastTemp, status >= PowerManager.THERMAL_STATUS_MODERATE)
    }

    fun start() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                lastTemp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    power.currentThermalStatus else PowerManager.THERMAL_STATUS_NONE
                onReading(lastTemp, status >= PowerManager.THERMAL_STATUS_MODERATE)
            }
        }
        receiver = r
        ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { power.addThermalStatusListener(statusListener) }
        }
    }

    fun stop() {
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        receiver = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { power.removeThermalStatusListener(statusListener) }
        }
    }
}

class NetworkMonitor(
    private val host: String,
    private val onSample: (rttMs: Int, jitterMs: Float, lossPercent: Float) -> Unit,
) {

    private val history = ArrayDeque<Int>()
    private var sent = 0
    private var lost = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            while (isActive) {
                val rtt = ping()
                sent++
                if (rtt < 0) lost++ else {
                    history.addLast(rtt)
                    if (history.size > 12) history.removeFirst()
                }
                val jitter = if (history.size >= 3) stddev(history.toList()) else 0f
                onSample(rtt, jitter, lost * 100f / sent.coerceAtLeast(1))
                delay(1000L)
            }
        }
    }

    fun stop() = scope.cancel()

    private fun ping(): Int = try {
        val p = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "1", "-W", "2", host))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        Regex("time=([0-9.]+)").find(out)?.groupValues?.get(1)?.toFloat()?.toInt() ?: -1
    } catch (_: Exception) {
        -1
    }

    private fun stddev(v: List<Int>): Float {
        val mean = v.average()
        return sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size).toFloat()
    }
}
