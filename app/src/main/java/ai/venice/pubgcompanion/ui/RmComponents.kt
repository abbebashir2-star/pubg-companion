package ai.venice.pubgcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.venice.pubgcompanion.core.PerfMode
import ai.venice.pubgcompanion.ui.theme.Bg2
import ai.venice.pubgcompanion.ui.theme.Edge
import ai.venice.pubgcompanion.ui.theme.FgMuted

@Composable
fun RmLabel(text: String, color: Color = FgMuted, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp,
    )
}

@Composable
fun RmCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Bg2)
            .border(1.dp, Edge, RoundedCornerShape(6.dp)),
    ) {
        if (accent != null) Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun RmStat(label: String, value: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Column(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Edge, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        RmLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

@Composable
fun RmToggle(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(sub, color = FgMuted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun RmModeSelector(selected: PerfMode?, onSelect: (PerfMode) -> Unit) {
    Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PerfMode.entries.forEach { mode ->
            val active = mode == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) mode.accent.copy(alpha = 0.16f) else Bg2)
                    .border(
                        if (active) 2.dp else 1.dp,
                        if (active) mode.accent else Edge,
                        RoundedCornerShape(4.dp),
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    mode.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = if (active) mode.accent else FgMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${mode.coresOnline}C",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (active) Color.White else FgMuted,
                )
            }
        }
    }
}
