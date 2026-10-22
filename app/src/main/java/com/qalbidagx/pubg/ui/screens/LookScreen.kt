package com.qalbidagx.pubg.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qalbidagx.pubg.data.SettingsStore
import com.qalbidagx.pubg.ui.Panel
import com.qalbidagx.pubg.ui.SectionTitle
import com.qalbidagx.pubg.ui.theme.Bad
import com.qalbidagx.pubg.ui.theme.Edge
import com.qalbidagx.pubg.ui.theme.Good
import com.qalbidagx.pubg.ui.theme.RmCyan
import kotlinx.coroutines.launch

class LookViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)
    val accent = settings.accentColor
    fun setAccent(v: Int) = viewModelScope.launch { settings.setAccent(v) }
}

private val ACCENTS = listOf(
    0xFFFF2A3C.toInt() to "Red Magic",
    0xFF00E5FF.toInt() to "Cyan",
    0xFF3DDC84.toInt() to "Green",
    0xFFFFB300.toInt() to "Amber",
    0xFFFF6D00.toInt() to "Orange",
    0xFF9C27B0.toInt() to "Purple",
    0xFFFF4081.toInt() to "Pink",
    0xFFFFFFFF.toInt() to "White",
)

@Composable
fun LookScreen(vm: LookViewModel = viewModel()) {
    val accent by vm.accent.collectAsStateWithLifecycle(initialValue = 0xFFFF2A3C.toInt())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Look", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Accent colour for the whole app.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Accent")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ACCENTS.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (value, label) ->
                            val active = accent == value
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(value))
                                        .border(
                                            if (active) 3.dp else 1.dp,
                                            if (active) Color.White else Edge,
                                            CircleShape,
                                        )
                                        .clickable { vm.setAccent(value) },
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    label, fontSize = 9.sp,
                                    color = if (active) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Buttons, mode selector and the overlay edge all follow this colour.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("Status colours")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OK", color = Good, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                Text("Tweak applied", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FAIL", color = Bad, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                Text("Kernel or permission blocked it", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("INFO", color = RmCyan, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                Text("Neutral reading", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
