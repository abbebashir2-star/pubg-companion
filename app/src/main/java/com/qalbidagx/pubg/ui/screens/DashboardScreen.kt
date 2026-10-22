package com.qalbidagx.pubg.ui.screens

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qalbidagx.pubg.core.PermissionKind
import com.qalbidagx.pubg.core.Permissions
import com.qalbidagx.pubg.core.PerfMode
import com.qalbidagx.pubg.data.AppDatabase
import com.qalbidagx.pubg.data.GameModeState
import com.qalbidagx.pubg.data.ProfileRepository
import com.qalbidagx.pubg.data.SettingsStore
import com.qalbidagx.pubg.service.GameModeService
import com.qalbidagx.pubg.system.ShizukuShell
import com.qalbidagx.pubg.ui.*
import com.qalbidagx.pubg.ui.theme.Bad
import com.qalbidagx.pubg.ui.theme.Good
import com.qalbidagx.pubg.ui.theme.RmRed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val repo = ProfileRepository(AppDatabase.get(app).profiles())

    val inGame = GameModeState.inGame
    val stats = GameModeState.stats
    val activeProfile = GameModeState.activeProfile
    val log = GameModeState.log
    val mode = GameModeState.mode
    val tweaks = GameModeState.tweaks
    val playtime = GameModeState.playtime

    val gameMode = settings.gameModeEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var missing by mutableStateOf<List<PermissionKind>>(emptyList())
        private set
    var shizuku by mutableStateOf(false)
        private set

    fun refresh() {
        missing = Permissions.missing(getApplication())
        shizuku = ShizukuShell.hasPermission()
    }

    fun toggleGameMode(on: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch { settings.setGameMode(on) }
        val i = Intent(app, GameModeService::class.java)
        if (on) ContextCompat.startForegroundService(app, i) else app.stopService(i)
    }

    fun requestShizuku() = ShizukuShell.requestPermission()

    fun selectMode(m: PerfMode) {
        viewModelScope.launch {
            val first = repo.enabled().firstOrNull() ?: return@launch
            repo.save(
                first.copy(
                    perfMode = m.id,
                    pinToBigCores = m.pinCores,
                    boostPriority = m.pinCores,
                )
            )
            GameModeState.note("Mode set to ${m.label}")
        }
    }
}

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val ctx = LocalContext.current
    val inGame by vm.inGame.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val active by vm.activeProfile.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val tweaks by vm.tweaks.collectAsStateWithLifecycle()
    val playtime by vm.playtime.collectAsStateWithLifecycle()
    val gameMode by vm.gameMode.collectAsStateWithLifecycle()

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("QALBIDAGX PUBG", fontSize = 22.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp, color = RmRed)
        Text("Gear up before you drop", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Performance mode")
        RmModeSelector(mode) { vm.selectMode(it) }
        Spacer(Modifier.height(8.dp))
        RmCard(accent = mode?.accent ?: RmRed) {
            Text(mode?.blurb ?: "Balanced default", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle("Status")
        RmCard(accent = if (inGame) RmRed else Good) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (inGame) "MATCH RUNNING" else "IDLE",
                    fontFamily = FontFamily.Monospace, fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (inGame) RmRed else Good,
                    modifier = Modifier.weight(1f),
                )
                if (inGame) {
                    Text(
                        "%d:%02d".format(playtime / 60, playtime % 60),
                        fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (active != null) {
                Text("Profile: $active", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RmStat("FPS", (if (stats.fpsIsReal) "" else "~") + stats.fps, Modifier.weight(1f))
                RmStat("CPU", "${stats.cpuLoad}%", Modifier.weight(1f))
                RmStat("SOC", "%.0f".format(stats.socTempC), Modifier.weight(1f),
                    if (stats.socTempC >= 43f) Bad else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RmStat("GPU", "${stats.gpuMhz}", Modifier.weight(1f))
                RmStat("BAT", "%.0f".format(stats.batteryTempC), Modifier.weight(1f))
                RmStat("NET", if (stats.pingMs >= 0) "${stats.pingMs}" else "--", Modifier.weight(1f))
            }
        }

        SectionTitle("Auto game mode")
        RmCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Watch for PUBG", fontSize = 14.sp)
                    Text("Applies your profile the moment a match starts",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = gameMode, onCheckedChange = { vm.toggleGameMode(it) })
            }
        }

        if (tweaks.isNotEmpty()) {
            SectionTitle("Last applied")
            RmCard {
                tweaks.forEach { t ->
                    Text(
                        "${if (t.ok) "OK" else "FAIL"}  ${t.name}",
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = if (t.ok) Good else Bad,
                    )
                    Text(t.detail, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionTitle("Permissions")
        RmCard {
            if (vm.missing.isEmpty()) {
                Text("All set", color = Good, fontSize = 13.sp)
            } else {
                vm.missing.forEach { kind ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(kind.title, fontSize = 13.sp)
                            Text(kind.why, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            ctx.startActivity(Permissions.intent(ctx, kind))
                        }) { Text("Grant") }
                    }
                }
            }
        }

        SectionTitle("Shizuku")
        RmCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ADB rights", fontSize = 14.sp)
                    Text(
                        if (vm.shizuku) "Active - full tuning available"
                        else "Missing - most tweaks disabled",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!vm.shizuku) {
                    TextButton(onClick = { vm.requestShizuku() }) { Text("Request") }
                }
            }
        }

        SectionTitle("Log")
        RmCard {
            if (log.isEmpty()) {
                Text("Nothing yet", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                log.reversed().take(30).forEach {
                    Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
