package ai.venice.pubgcompanion.service

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.View
import ai.venice.pubgcompanion.data.GameModeState

class StatsOverlayView(context: Context) : View(context) {

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6000000.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF2A3C.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF2A3C.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val history = ArrayDeque<Int>()
    private var lastSecond = 0L
    private var frames = 0
    private val choreographer = Choreographer.getInstance()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frames++
            val now = System.nanoTime()
            if (lastSecond == 0L) lastSecond = now
            if (now - lastSecond >= 1_000_000_000L) {
                val measured = frames
                frames = 0
                lastSecond = now
                history.addLast(measured)
                if (history.size > 40) history.removeFirst()
                GameModeState.updateStats { s ->
                    if (s.fpsIsReal) s else s.copy(fps = measured)
                }
                invalidate()
            }
            choreographer.postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        choreographer.removeFrameCallback(frameCallback)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(WIDTH_PX, (text.textSize * 1.5f * 4 + 20f + GRAPH_H).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val s = GameModeState.stats.value
        val rows = listOf(
            (if (s.fpsIsReal) "" else "~") + s.fps.toString().padStart(3) + " FPS",
            "%.1f C".format(s.batteryTempC) + if (s.throttling) "  THR" else "",
            if (s.pingMs >= 0) s.pingMs.toString().padStart(3) + " ms" else " -- ms",
            "loss %.1f%%".format(s.lossPercent),
        )

        val h = text.textSize * 1.5f * rows.size + 20f + GRAPH_H
        canvas.drawRoundRect(0f, 0f, WIDTH_PX.toFloat(), h, 6f, 6f, bg)
        canvas.drawRect(0f, 0f, 4f, h, edge)

        val graphTop = text.textSize * 1.5f * rows.size + 12f
        if (history.size > 1) {
            val path = Path()
            val stepX = WIDTH_PX / history.size.toFloat()
            val maxFps = history.max().coerceAtLeast(60).toFloat()
            history.forEachIndexed { i, v ->
                val x = i * stepX
                val y = graphTop + GRAPH_H - (v / maxFps) * GRAPH_H
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, line)
        }

        rows.forEachIndexed { i, t ->
            canvas.drawText(t, 16f, 18f + text.textSize + i * text.textSize * 1.5f, text)
        }
    }

    private companion object {
        const val WIDTH_PX = 340
        const val GRAPH_H = 34f
    }
}
