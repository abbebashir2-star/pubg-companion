package com.qalbidagx.pubg.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.qalbidagx.pubg.data.CrosshairStyle
import com.qalbidagx.pubg.data.GameModeState
import com.qalbidagx.pubg.data.SettingsStore
import com.qalbidagx.pubg.system.NetworkMonitor
import com.qalbidagx.pubg.system.SysfsProbe

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var statsView: StatsOverlayView? = null
    private var crosshairView: CrosshairView? = null
    private var net: NetworkMonitor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val probe = SysfsProbe()

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIF_ID,
            Notifier.build(this, Notifier.CH_OVERLAY, "Overlay active", "Showing stats")
        )
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        rebuild()
        scope.launch {
            while (isActive) {
                runCatching { GameModeState.applySys(probe.snapshot()) }
                delay(1000L)
            }
        }
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

        val s = SettingsStore(this)
        if (runBlocking { s.overlayEnabled.first() }) {
            addStats(runBlocking { s.overlayTouchable.first() })
        }
        if (runBlocking { s.crosshairEnabled.first() }) {
            addCrosshair(
                style = CrosshairStyle(
                    shape = runBlocking { s.crosshairShape.first() },
                    color = runBlocking { s.crosshairColor.first() },
                    sizePx = runBlocking { s.crosshairSize.first() },
                    thicknessPx = runBlocking { s.crosshairThickness.first() },
                    gapPx = runBlocking { s.crosshairGap.first() },
                    dotRadiusPx = runBlocking { s.crosshairDot.first() },
                    alpha = runBlocking { s.crosshairAlpha.first() },
                    outline = runBlocking { s.crosshairOutline.first() },
                ),
                touchable = runBlocking { s.crosshairTouchable.first() },
                startX = runBlocking { s.crosshairX.first() },
                startY = runBlocking { s.crosshairY.first() },
            )
        }

        val host = runBlocking { s.pingHost.first() }
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
            y = 150
        }

        if (touchable) attachDrag(view, params) { _, _ -> }
        wm.addView(view, params)
        statsView = view
    }

    private fun addCrosshair(style: CrosshairStyle, touchable: Boolean, startX: Int, startY: Int) {
        val view = CrosshairView(this).apply { crosshair = style }
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
            x = startX
            y = startY
        }

        if (touchable) {
            attachDrag(view, params) { x, y ->
                scope.launch { SettingsStore(this@OverlayService).setCrosshairPos(x, y) }
            }
        }

        wm.addView(view, params)
        crosshairView = view
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams, onEnd: (Int, Int) -> Unit) {
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
                MotionEvent.ACTION_UP -> {
                    onEnd(params.x, params.y)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        net?.stop()
        statsView?.let { runCatching { wm.removeView(it) } }
        crosshairView?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object { const val NOTIF_ID = 1002 }
}
