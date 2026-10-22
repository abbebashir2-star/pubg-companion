package com.qalbidagx.pubg.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.qalbidagx.pubg.data.GameModeState
import java.io.File

class RecorderService : Service() {

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var wm: WindowManager? = null
    private var button: View? = null
    private var outputPath = ""

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        if (recorder == null) startRecording(intent)
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startRecording(intent: Intent?) {
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return
        }

        val notif = Notifier.recording(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = runCatching { mpm.getMediaProjection(code, data) }.getOrNull()
            ?: run { stopSelf(); return }

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val dpi = dm.densityDpi

        val dir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        outputPath = File(dir, "qalbidagx-${System.currentTimeMillis()}.mp4").absolutePath

        val withAudio = intent.getBooleanExtra(EXTRA_AUDIO, false)

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()

        runCatching {
            if (withAudio) {
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setVideoSize(w, h)
            rec.setVideoFrameRate(60)
            rec.setVideoEncodingBitRate(w * h * 6)
            rec.setOutputFile(outputPath)
            rec.prepare()
        }.onFailure {
            GameModeState.note("Recorder prepare failed: ${it.message}")
            stopSelf()
            return
        }

        display = projection?.createVirtualDisplay(
            "qalbidagx-rec", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            rec.surface, null, null,
        )

        runCatching { rec.start() }.onFailure {
            GameModeState.note("Recorder start failed: ${it.message}")
            stopSelf()
            return
        }

        recorder = rec
        GameModeState.setRecording(true)
        GameModeState.note("Recording started")
        addButton()
    }

    private fun stopRecording() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { display?.release() }
        display = null
        runCatching { projection?.stop() }
        projection = null
        removeButton()
        GameModeState.setRecording(false)
        GameModeState.note("Saved: $outputPath")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun addButton() {
        if (button != null) return
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager
        val size = (52 * resources.displayMetrics.density).toInt()

        val v = object : View(this) {
            private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 255, 42, 60)
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            override fun onDraw(canvas: Canvas) {
                val r = (size / 2f) - 8f
                canvas.drawCircle(size / 2f, size / 2f, r, ring)
                val s = r * 0.55f
                canvas.drawRect(
                    size / 2f - s / 2f, size / 2f - s / 2f,
                    size / 2f + s / 2f, size / 2f + s / 2f, box,
                )
            }
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 24
        }

        var downX = 0; var downY = 0; var startX = 0; var startY = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX.toInt(); downY = e.rawY.toInt()
                    startX = params.x; startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX.toInt() - downX) > 12 ||
                        kotlin.math.abs(e.rawY.toInt() - downY) > 12
                    ) moved = true
                    params.x = startX + (e.rawX.toInt() - downX)
                    params.y = startY + (e.rawY.toInt() - downY)
                    runCatching { manager.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        ContextCompat.startForegroundService(
                            this@RecorderService,
                            Intent(this@RecorderService, RecorderService::class.java)
                                .setAction(ACTION_STOP),
                        )
                    }
                    true
                }
                else -> false
            }
        }

        runCatching { manager.addView(v, params) }
        button = v
    }

    private fun removeButton() {
        button?.let { b -> wm?.let { runCatching { it.removeView(b) } } }
        button = null
        wm = null
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.qalbidagx.pubg.STOP_RECORD"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_AUDIO = "audio"
        private const val NOTIF_ID = 1003
    }
}
