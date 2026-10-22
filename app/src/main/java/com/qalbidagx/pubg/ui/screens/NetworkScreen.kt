package com.qalbidagx.pubg.ui.screens

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qalbidagx.pubg.data.SettingsStore
import com.qalbidagx.pubg.system.NetworkMonitor
import com.qalbidagx.pubg.ui.Panel
import com.qalbidagx.pubg.ui.SectionTitle
import com.qalbidagx.pubg.ui.theme.Bad
import com.qalbidagx.pubg.ui.theme.Good
import com.qalbidagx.pubg.ui.theme.RmRed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NetworkViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private var monitor: NetworkMonitor? = null

    var host by mutableStateOf("1.1.1.1")
        private set
    var rtt by mutableIntStateOf(-1)
        private set
    var jitter by mutableFloatStateOf(0f)
        private set
    var loss by mutableFloatStateOf(0f)
        private set
    var samples by mutableIntStateOf(0)
        private set

    val history = mutableStateListOf<Int>()

    init {
        viewModelScope.launch {
            host = settings.pingHost.first()
            restart()
        }
    }

    fun setHost(h: String) {
        host = h
        viewModelScope.launch { settings.setPingHost(h) }
    }

    fun restart() {
        monitor?.stop()
        monitor = NetworkMonitor(host) { r, j, l ->
            rtt = r
            jitter = j
            loss = l
            samples++
            history.add(if (r > 0) r else 0)
            if (history.size > 60) history.removeAt(0)
        }.also { it.start() }
    }

    override fun onCleared() {
        monitor?.stop()
        super.onCleared()
    }
}

@Composable
fun NetworkScreen(vm: NetworkViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Network", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Point this at your game server region for the numbers to mean anything.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Host")
        Panel {
            OutlinedTextField(
                value = vm.host,
                onValueChange = { vm.setHost(it) },
                label = { Text("IP or domain") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1.1.1.1", "8.8.8.8", "9.9.9.9").forEach { preset ->
                    AssistChip(
                        onClick = { vm.setHost(preset); vm.restart() },
                        label = { Text(preset) },
                    )
                }
            }
        }

        SectionTitle("Live")
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("RTT", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (vm.rtt >= 0) "${vm.rtt} ms" else "lost",
                        fontSize = 18.sp,
                        color = if (vm.rtt >= 0) MaterialTheme.colorScheme.onSurface else Bad,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("JITTER", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.1f ms".format(vm.jitter), fontSize = 18.sp,
                        color = if (vm.jitter > 25f) Bad else Good)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Loss %.1f%%  ·  %d samples".format(vm.loss, vm.samples), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                if (vm.history.size < 2) return@Canvas
                val maxV = (vm.history.maxOrNull() ?: 100).coerceAtLeast(50).toFloat()
                val stepX = size.width / (vm.history.size - 1).coerceAtLeast(1)
                val path = Path()
                vm.history.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = size.height - (v / maxV) * (size.height - 6f) - 3f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = RmRed, style = Stroke(width = 3f))
            }

            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.restart() }, modifier = Modifier.fillMaxWidth()) {
                Text("Restart measurement")
            }
        }

        SectionTitle("How to read it")
        Panel {
            Text(
                "Under 60 ms is fine. Jitter matters more than the average - high jitter " +
                    "feels like bullets not registering even when ping looks good.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
