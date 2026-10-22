package com.qalbidagx.pubg.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qalbidagx.pubg.data.AppDatabase
import com.qalbidagx.pubg.data.Profile
import com.qalbidagx.pubg.data.ProfileRepository
import com.qalbidagx.pubg.ui.ToggleRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProfileRepository(AppDatabase.get(app).profiles())
    val profiles = repo.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun save(p: Profile) = viewModelScope.launch { repo.save(p) }
    fun delete(p: Profile) = viewModelScope.launch { repo.delete(p) }
}

@Composable
fun ProfilesScreen(vm: ProfilesViewModel = viewModel()) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Profile?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Profiles", fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text("The first enabled profile is used when a match starts.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
        items(profiles, key = { it.id }) { p ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable { editing = p },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            buildString {
                                append(p.perfMode.uppercase())
                                append("  ·  ${p.refreshRateHz} Hz")
                                if (p.pinToBigCores) append("  ·  pinned")
                                if (p.chargeSeparation) append("  ·  no charge")
                                if (p.lowerResolution) {
                                    append("  ·  ${p.resolutionWidth}x${p.resolutionHeight}")
                                }
                                if (!p.enabled) append("  ·  off")
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.delete(p) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { editing = Profile(name = "New profile") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New profile")
            }
        }
    }

    editing?.let { p ->
        ProfileEditor(p, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null })
    }
}

@Composable
private fun ProfileEditor(initial: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var p by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New profile" else "Edit profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = p.name,
                    onValueChange = { p = p.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow("Enabled", p.enabled) { p = p.copy(enabled = it) }
                ToggleRow("Do Not Disturb", p.dnd) { p = p.copy(dnd = it) }
                ToggleRow("Pin to big cores", p.pinToBigCores) { p = p.copy(pinToBigCores = it) }
                ToggleRow("Raise process priority", p.boostPriority) {
                    p = p.copy(boostPriority = it)
                }
                ToggleRow("Restrict background apps", p.restrictBackground) {
                    p = p.copy(restrictBackground = it)
                }
                ToggleRow("Charge separation", p.chargeSeparation) {
                    p = p.copy(chargeSeparation = it)
                }
                ToggleRow("Lower resolution (Shizuku)", p.lowerResolution) {
                    p = p.copy(lowerResolution = it)
                }
                ToggleRow("Force stop bloat", p.killBackground) {
                    p = p.copy(killBackground = it)
                }
                ToggleRow("Thermal guard", p.thermalGuard) { p = p.copy(thermalGuard = it) }

                Spacer(Modifier.height(12.dp))
                Text("Brightness: ${p.brightnessPercent}%", fontSize = 11.sp)
                Slider(
                    value = p.brightnessPercent.toFloat(),
                    onValueChange = { p = p.copy(brightnessPercent = it.toInt()) },
                    valueRange = 20f..100f,
                )

                Text("Refresh rate: ${p.refreshRateHz} Hz", fontSize = 11.sp)
                Slider(
                    value = p.refreshRateHz.toFloat(),
                    onValueChange = { p = p.copy(refreshRateHz = it.toInt()) },
                    valueRange = 60f..165f,
                    steps = 4,
                )

                if (p.lowerResolution) {
                    Text("Height: ${p.resolutionHeight} px", fontSize = 11.sp)
                    Slider(
                        value = p.resolutionHeight.toFloat(),
                        onValueChange = {
                            val h = it.toInt()
                            p = p.copy(
                                resolutionHeight = h,
                                resolutionWidth = h * 16 / 9,
                                resolutionDpi = (h * 240 / 1080f).toInt().coerceIn(160, 480),
                            )
                        },
                        valueRange = 540f..1080f,
                        steps = 3,
                    )
                }

                if (p.thermalGuard) {
                    Text("Temp limit: ${p.temperatureLimitC} C", fontSize = 11.sp)
                    Slider(
                        value = p.temperatureLimitC.toFloat(),
                        onValueChange = { p = p.copy(temperatureLimitC = it.toInt()) },
                        valueRange = 35f..50f,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (p.name.isNotBlank()) onSave(p) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
