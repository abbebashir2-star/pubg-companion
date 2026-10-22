package ai.venice.pubgcompanion.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import ai.venice.pubgcompanion.data.GameModeState
import ai.venice.pubgcompanion.data.SettingsStore
import ai.venice.pubgcompanion.system.NetworkMonitor

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var statsView: StatsOverlayView? = null
    private var crosshairView: View? = null
    private var net: NetworkMonitor? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIF_ID,
            Notifier.build(this, Notifier.CH_OVERLAY, "Overlay active", "Showing stats")
        )
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        rebuild()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        rebuild()
        return START_STICKY
    }

    private fun rebuild() {
        statsView?.let { runCatching { wm.removeView(it) } }
        crosshairView?.let { runCatching { wm.removeView(it) } }
        statsView = null
        crosshairView = null
        net?.stop()
        net = null

        val settings = SettingsStore(this)
        val showStats = runBlocking { settings.overlayEnabled.first() }
        val touchable = runBlocking { settings.overlayTouchable.first() }
        val crosshair = runBlocking { settings.showCrosshair.first() }
        val crosshairSize = runBlocking { settings.crosshairSize.first() }

        if (showStats) addStats(touchable)
        if (crosshair) addCrosshair(crosshairSize)

        val host = runBlocking { settings.pingHost.first() }
        net = NetworkMonitor(host) { rtt, jitter, loss ->
            GameModeState.updateStats {
                it.copy(pingMs = rtt, jitterMs = jitter, lossPercent = loss)
            }
        }.also { it.start() }
    }

    private fun addStats(touchable: Boolean) {
        val view = StatsOverlayView(this)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 140
        }

        if (touchable) attachDrag(view, params)
        wm.addView(view, params)
        statsView = view
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        var downX = 0; var downY = 0; var startX = 0; var startY = 0
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX.toInt(); downY = e.rawY.toInt()
                    startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX.toInt() - downX)
                    params.y = startY + (e.rawY.toInt() - downY)
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun addCrosshair(sizePx: Int) {
        val view = object : View(this) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(215, 255, 42, 60)
                strokeWidth = 3f
            }
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val gap = sizePx / 2f
                canvas.drawLine(cx - gap - sizePx, cy, cx - gap, cy, p)
                canvas.drawLine(cx + gap, cy, cx + gap + sizePx, cy, p)
                canvas.drawLine(cx, cy - gap - sizePx, cx, cy - gap, p)
                canvas.drawLine(cx, cy + gap, cx, cy + gap + sizePx, p)
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(view, params)
        crosshairView = view
    }

    override fun onDestroy() {
        net?.stop()
        statsView?.let { runCatching { wm.removeView(it) } }
        crosshairView?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object { const val NOTIF_ID = 1002 }
}
