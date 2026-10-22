package com.qalbidagx.pubg.service

import android.content.Context
import android.graphics.*
import android.view.View
import com.qalbidagx.pubg.data.CrosshairStyle

class CrosshairView(context: Context) : View(context) {

    var crosshair: CrosshairStyle = CrosshairStyle()
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val reach = crosshair.gapPx + crosshair.sizePx + crosshair.thicknessPx * 3
        val side = (reach * 2).coerceAtLeast(crosshair.dotRadiusPx * 8).coerceAtLeast(48)
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val c = crosshair.color
        val a = crosshair.alpha
        val argb = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

        stroke.color = argb
        fill.color = argb
        stroke.strokeWidth = crosshair.thicknessPx.toFloat()
        outline.strokeWidth = crosshair.thicknessPx + 2f
        outline.color = Color.argb((a * 0.55f).toInt(), 0, 0, 0)

        val gap = crosshair.gapPx.toFloat()
        val len = crosshair.sizePx.toFloat()

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            if (crosshair.outline) canvas.drawLine(x1, y1, x2, y2, outline)
            canvas.drawLine(x1, y1, x2, y2, stroke)
        }

        fun circle(radius: Float) {
            if (crosshair.outline) canvas.drawCircle(cx, cy, radius, outline)
            canvas.drawCircle(cx, cy, radius, stroke)
        }

        fun arms() {
            line(cx - gap - len, cy, cx - gap, cy)
            line(cx + gap, cy, cx + gap + len, cy)
            line(cx, cy - gap - len, cx, cy - gap)
            line(cx, cy + gap, cx, cy + gap + len)
        }

        fun dot() {
            if (crosshair.outline) {
                canvas.drawCircle(cx, cy, crosshair.dotRadiusPx + 1.6f, outline)
            }
            canvas.drawCircle(cx, cy, crosshair.dotRadiusPx.toFloat(), fill)
        }

        when (crosshair.shape) {
            "dot" -> dot()
            "circle" -> circle(gap + len / 2f)
            "cross" -> arms()
            "tee" -> {
                line(cx - gap - len, cy, cx - gap, cy)
                line(cx + gap, cy, cx + gap + len, cy)
                line(cx, cy + gap, cx, cy + gap + len)
            }
            "chevron" -> {
                val p = Path()
                p.moveTo(cx - len, cy + len / 2f)
                p.lineTo(cx, cy - len / 2f)
                p.lineTo(cx + len, cy + len / 2f)
                if (crosshair.outline) canvas.drawPath(p, outline)
                canvas.drawPath(p, stroke)
            }
            else -> {
                circle(gap + len / 2f)
                arms()
                dot()
            }
        }
    }
}
