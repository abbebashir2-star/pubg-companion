package com.qalbidagx.pubg.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qalbidagx.pubg.core.GamePackages
import com.qalbidagx.pubg.data.GameModeState
import com.qalbidagx.pubg.system.ChargeController
import com.qalbidagx.pubg.system.DeviceProfiler
import com.qalbidagx.pubg.system.PerfTuner
import com.qalbidagx.pubg.system.ProcessTuner
import com.qalbidagx.pubg.system.ShizukuShell
import com.qalbidagx.pubg.system.ThermalReader
import com.qalbidagx.pubg.system.Tweak
import com.qalbidagx.pubg.ui.Panel
import com.qalbidagx.pubg.ui.SectionTitle
import com.qalbidagx.pubg.ui.theme.Bad
import com.qalbidagx.pubg.ui.theme.Good
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemViewModel(app: Application) : AndroidViewModel(app) {

    var report by mutableStateOf<List<Tweak>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set

    val device = GameModeState.device

    fun runDiagnostics() {
        if (running) return
        running = true
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                val list = mutableListOf<Tweak>()
                val sh = ShizukuShell.hasPermission()
                list += Tweak("Shizuku", sh, if (sh) "granted" else "not granted")

                val dev = runCatching { DeviceProfiler.profile() }.getOrNull()
                    ?: return@withContext list
                list += Tweak("SoC", true, dev.soc.ifBlank { "unknown" })
                list += Tweak("Cores", true, "${dev.cores} total, big ${dev.bigCores}")
                list += Tweak("Affinity mask", true, "0x${dev.clusterMaskHex}")
                list += Tweak("Max refresh", true, "${dev.maxRefreshHz} Hz")
                list += Tweak("GPU devfreq node", dev.gpuPath != null, dev.gpuPath ?: "none")
                list += Tweak("Thermal zones", dev.thermalZones > 0, "${dev.thermalZones} zones")
                list += Tweak("taskset", dev.tasksetAvailable, "core pinning")
                list += Tweak("chrt", dev.chrtAvailable, "real-time priority")

                val hot = ThermalReader.hottest()
                list += Tweak(
                    "Hottest zone", hot != null,
                    hot?.let { "${it.first} %.1f C".format(it.second) } ?: "n/a"
                )

                val gov = ShizukuShell.exec("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                list += Tweak("Governor read", gov != null, gov ?: "unreadable")
                list += Tweak(
                    "Governor write",
                    ShizukuShell.ok("echo schedutil > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"),
                    "kernel may block this"
                )

                list += Tweak(
                    "cmd power",
                    ShizukuShell.exec("cmd power get-fixed-performance-mode-enabled") != null,
                    "fixed performance mode"
                )
                list += Tweak(
                    "cmd thermalservice",
                    ShizukuShell.exec("cmd thermalservice get-current-thermal-status") != null,
                    "thermal override"
                )

                val pid = ProcessTuner.report(GamePackages.PRIMARY)
                list += Tweak("Game process", pid != "not running", pid)
                list
            }
            report = out
            running = false
        }
    }

    fun testCharge() {
        viewModelScope.launch {
            val (ok, detail) = withContext(Dispatchers.IO) { ChargeController.setBypass(true) }
            report = report + Tweak("Charge separation", ok, detail)
            GameModeState.note("Charge test: $detail")
        }
    }

    fun compileGame() {
        viewModelScope.launch {
            val t = withContext(Dispatchers.IO) { PerfTuner.aotCompile(GamePackages.PRIMARY) }
            GameModeState.note("${t.name}: ${t.detail}")
        }
    }

    fun dexopt() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PerfTuner.dexopt() }
            GameModeState.note("Dexopt queued")
        }
    }
}

@Composable
fun SystemScreen(vm: SystemViewModel = viewModel()) {
    val dev by vm.device.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("System", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("What your hardware actually supports. Nothing is assumed.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Device")
        Panel {
            val d = dev
            if (d == null) {
                Text("Profiling...", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                InfoRow("SoC", d.soc.ifBlank { "unknown" })
                InfoRow("Board", d.board.ifBlank { "unknown" })
                InfoRow("Cores", d.cores.toString())
                InfoRow("Big cluster", d.bigCores.joinToString(", "))
                InfoRow("Affinity mask", "0x${d.clusterMaskHex}")
                InfoRow("Max refresh", "${d.maxRefreshHz} Hz")
                InfoRow("GPU node", d.gpuPath ?: "none found")
                InfoRow("Thermal zones", d.thermalZones.toString())
            }
        }

        SectionTitle("Diagnostics")
        Panel {
            Button(
                onClick = { vm.runDiagnostics() },
                enabled = !vm.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.running) "Running..." else "Run full check") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.testCharge() }, modifier = Modifier.fillMaxWidth()) {
                Text("Test charge separation")
            }

            if (vm.report.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                vm.report.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            if (t.ok) "OK" else "FAIL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (t.ok) Good else Bad,
                            modifier = Modifier.width(44.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontSize = 12.sp)
                            Text(t.detail, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        SectionTitle("Maintenance")
        Panel {
            Button(onClick = { vm.compileGame() }, modifier = Modifier.fillMaxWidth()) {
                Text("AOT compile PUBG")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.dexopt() }, modifier = Modifier.fillMaxWidth()) {
                Text("Run dexopt")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Compiling ahead of time makes the first match load faster and reduces " +
                    "stutter while shaders warm up. Run it once.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}
