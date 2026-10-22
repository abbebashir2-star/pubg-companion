package com.qalbidagx.pubg.ui.screens

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qalbidagx.pubg.data.CrosshairStyle
import com.qalbidagx.pubg.data.GameModeState
import com.qalbidagx.pubg.data.SettingsStore
import com.qalbidagx.pubg.service.OverlayService
import com.qalbidagx.pubg.ui.Panel
import com.qalbidagx.pubg.ui.RecorderController
import com.qalbidagx.pubg.ui.SectionTitle
import com.qalbidagx.pubg.ui.ToggleRow
import com.qalbidagx.pubg.ui.theme.Bg2
import com.qalbidagx.pubg.ui.theme.Edge
import com.qalbidagx.pubg.ui.theme.Good
import com.qalbidagx.pubg.ui.theme.RmRed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OverlayViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)

    val enabled = settings.overlayEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val touchable = settings.overlayTouchable.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val xOn = settings.crosshairEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val xTouch = settings.crosshairTouchable.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val shape = settings.crosshairShape.stateIn(viewModelScope, SharingStarted.Eagerly, "crossdot")
    val color = settings.crosshairColor.stateIn(viewModelScope, SharingStarted.Eagerly, 0xFFFF2A3C.toInt())
    val size = settings.crosshairSize.stateIn(viewModelScope, SharingStarted.Eagerly, 22)
    val thickness = settings.crosshairThickness.stateIn(viewModelScope, SharingStarted.Eagerly, 3)
    val gap = settings.crosshairGap.stateIn(viewModelScope, SharingStarted.Eagerly, 8)
    val dot = settings.crosshairDot.stateIn(viewModelScope, SharingStarted.Eagerly, 3)
    val alpha = settings.crosshairAlpha.stateIn(viewModelScope, SharingStarted.Eagerly, 220)
    val outline = settings.crosshairOutline.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val recording = GameModeState.recording

    private fun push() {
        val app = getApplication<Application>()
        ContextCompat.startForegroundService(app, Intent(app, OverlayService::class.java))
    }

    fun setEnabled(v: Boolean) = viewModelScope.launch { settings.setOverlay(v); push() }
    fun setTouchable(v: Boolean) = viewModelScope.launch { settings.setTouchable(v); push() }
    fun setXOn(v: Boolean) = viewModelScope.launch { settings.setCrosshairEnabled(v); push() }
    fun setXTouch(v: Boolean) = viewModelScope.launch { settings.setCrosshairTouchable(v); push() }
    fun setShape(v: String) = viewModelScope.launch { settings.setCrosshairShape(v); push() }
    fun setColor(v: Int) = viewModelScope.launch { settings.setCrosshairColor(v); push() }
    fun setSize(v: Int) = viewModelScope.launch { settings.setCrosshairSize(v); push() }
    fun setThickness(v: Int) = viewModelScope.launch { settings.setCrosshairThickness(v); push() }
    fun setGap(v: Int) = viewModelScope.launch { settings.setCrosshairGap(v); push() }
    fun setDot(v: Int) = viewModelScope.launch { settings.setCrosshairDot(v); push() }
    fun setAlpha(v: Int) = viewModelScope.launch { settings.setCrosshairAlpha(v); push() }
    fun setOutline(v: Boolean) = viewModelScope.launch { settings.setCrosshairOutline(v); push() }
    fun recenter() = viewModelScope.launch { settings.setCrosshairPos(0, 0); push() }
}

@Composable
fun OverlayScreen(vm: OverlayViewModel = viewModel()) {
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val touchable by vm.touchable.collectAsStateWithLifecycle()
    val xOn by vm.xOn.collectAsStateWithLifecycle()
    val xTouch by vm.xTouch.collectAsStateWithLifecycle()
    val shape by vm.shape.collectAsStateWithLifecycle()
    val color by vm.color.collectAsStateWithLifecycle()
    val size by vm.size.collectAsStateWithLifecycle()
    val thickness by vm.thickness.collectAsStateWithLifecycle()
    val gap by vm.gap.collectAsStateWithLifecycle()
    val dot by vm.dot.collectAsStateWithLifecycle()
    val alpha by vm.alpha.collectAsStateWithLifecycle()
    val outline by vm.outline.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()

    val preview = CrosshairStyle(shape, color, size, thickness, gap, dot, alpha, outline)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Aim", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Crosshair, stats overlay and recording.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Crosshair")
        Panel {
            ToggleRow("Show crosshair", xOn) { vm.setXOn(it) }
            ToggleRow("Draggable", xTouch) { vm.setXTouch(it) }

            if (xOn) {
                Spacer(Modifier.height(14.dp))

                Box(
                    Modifier.fillMaxWidth().height(150.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0B0B0E))
                        .border(1.dp, Edge, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CrosshairPreview(preview)
                }

                Spacer(Modifier.height(14.dp))
                Text("Shape", fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CrosshairStyle.SHAPES.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (id, label) ->
                                val active = shape == id
                                Box(
                                    Modifier.weight(1f).height(34.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (active) RmRed.copy(alpha = 0.18f) else Bg2)
                                        .border(
                                            if (active) 2.dp else 1.dp,
                                            if (active) RmRed else Edge,
                                            RoundedCornerShape(4.dp),
                                        )
                                        .clickable { vm.setShape(id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label, fontSize = 10.sp,
                                        color = if (active) RmRed
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Colour", fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CrosshairStyle.COLORS.forEach { (value, _) ->
                        val active = color == value
                        Box(
                            Modifier.size(30.dp)
                                .clip(CircleShape)
                                .background(Color(value))
                                .border(
                                    if (active) 3.dp else 1.dp,
                                    if (active) Color.White else Edge,
                                    CircleShape,
                                )
                                .clickable { vm.setColor(value) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                SliderRow("Size", size, 8, 60) { vm.setSize(it) }
                SliderRow("Thickness", thickness, 1, 10) { vm.setThickness(it) }
                SliderRow("Gap", gap, 0, 30) { vm.setGap(it) }
                SliderRow("Dot", dot, 0, 12) { vm.setDot(it) }
                SliderRow("Opacity", alpha, 60, 255) { vm.setAlpha(it) }
                ToggleRow("Black outline", outline) { vm.setOutline(it) }

                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { vm.recenter() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Recentre crosshair")
                }
            }
        }

        SectionTitle("Stats overlay")
        Panel {
            ToggleRow("Show live stats", enabled) { vm.setEnabled(it) }
            ToggleRow("Movable", touchable) { vm.setTouchable(it) }
            Spacer(Modifier.height(8.dp))
            Text(
                "FPS, CPU load, CPU and GPU clocks, SoC temperature, battery current, " +
                    "network jitter and packet loss.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("Recording")
        Panel {
            Button(
                onClick = {
                    if (recording) RecorderController.stop?.invoke()
                    else RecorderController.start?.invoke()
                },
                colors = if (recording) {
                    ButtonDefaults.buttonColors(containerColor = RmRed)
                } else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (recording) "Stop recording" else "Start recording")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (recording) "A red button floats on screen. Tap it to stop."
                else "Records the screen at 60 fps with microphone audio.",
                fontSize = 11.sp,
                color = if (recording) Good else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Saved to Android/data/com.qalbidagx.pubg/files/recordings",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SliderRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text("$label: $value", fontSize = 11.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

@Composable
private fun CrosshairPreview(style: CrosshairStyle) {
    val scale = 1.9f
    val c = Color(style.color).copy(alpha = style.alpha / 255f)
    val outlineColor = Color.Black.copy(alpha = style.alpha / 255f * 0.55f)

    Canvas(Modifier.size(140.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val gap = style.gapPx * scale
        val len = style.sizePx * scale
        val th = style.thicknessPx * scale
        val dr = style.dotRadiusPx * scale

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            if (style.outline) {
                drawLine(outlineColor, Offset(x1, y1), Offset(x2, y2), th + 3f)
            }
            drawLine(c, Offset(x1, y1), Offset(x2, y2), th)
        }

        fun arms() {
            line(cx - gap - len, cy, cx - gap, cy)
            line(cx + gap, cy, cx + gap + len, cy)
            line(cx, cy - gap - len, cx, cy - gap)
            line(cx, cy + gap, cx, cy + gap + len)
        }

        fun ring(r: Float) {
            if (style.outline) drawCircle(outlineColor, r, Offset(cx, cy), style = Stroke(th + 3f))
            drawCircle(c, r, Offset(cx, cy), style = Stroke(th))
        }

        fun dot() {
            if (style.outline) drawCircle(outlineColor, dr + 2.5f, Offset(cx, cy))
            drawCircle(c, dr, Offset(cx, cy))
        }

        when (style.shape) {
            "dot" -> dot()
            "circle" -> ring(gap + len / 2f)
            "cross" -> arms()
            "tee" -> {
                line(cx - gap - len, cy, cx - gap, cy)
                line(cx + gap, cy, cx + gap + len, cy)
                line(cx, cy + gap, cx, cy + gap + len)
            }
            "chevron" -> {
                val p = Path().apply {
                    moveTo(cx - len, cy + len / 2f)
                    lineTo(cx, cy - len / 2f)
                    lineTo(cx + len, cy + len / 2f)
                }
                if (style.outline) drawPath(p, outlineColor, style = Stroke(th + 3f))
                drawPath(p, c, style = Stroke(th))
            }
            else -> {
                ring(gap + len / 2f)
                arms()
                dot()
            }
        }
    }
}
