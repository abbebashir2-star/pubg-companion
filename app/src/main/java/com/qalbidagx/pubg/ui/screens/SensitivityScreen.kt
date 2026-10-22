package com.qalbidagx.pubg.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qalbidagx.pubg.data.SettingsStore
import com.qalbidagx.pubg.ui.Panel
import com.qalbidagx.pubg.ui.SectionTitle
import com.qalbidagx.pubg.util.SensValues
import com.qalbidagx.pubg.util.SensitivityMath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SensitivityViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)

    var values by mutableStateOf(SensValues())
        private set
    var fromDpi by mutableIntStateOf(400)
        private set
    var toDpi by mutableIntStateOf(400)
        private set
    var code by mutableStateOf("")
        private set
    var importText by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val stored = settings.sensitivityCode.first()
            if (stored.isNotBlank()) {
                SensitivityMath.decode(stored)?.let { (v, d) ->
                    values = v
                    fromDpi = d
                    toDpi = d
                    code = stored
                }
            }
        }
    }

    fun setValue(index: Int, v: Float) { values = values.with(index, v) }
    fun setFromDpi(d: Int) { fromDpi = d.coerceIn(100, 800) }
    fun setToDpi(d: Int) { toDpi = d.coerceIn(100, 800) }
    fun setImportText(t: String) { importText = t }

    fun applyConversion() {
        val from = fromDpi
        values = SensitivityMath.convertAll(values, from, toDpi)
        fromDpi = toDpi
        message = "Converted from $from to $toDpi dpi"
    }

    fun export() {
        code = SensitivityMath.encode(values, fromDpi)
        viewModelScope.launch { settings.setSensitivityCode(code) }
        message = "Code created and saved"
    }

    fun doImport() {
        val decoded = SensitivityMath.decode(importText)
        if (decoded == null) {
            message = "Could not read that code"
            return
        }
        val (v, d) = decoded
        values = v
        fromDpi = d
        toDpi = d
        code = importText.trim()
        viewModelScope.launch { settings.setSensitivityCode(code) }
        message = "Code loaded"
    }

    fun reset() {
        values = SensValues()
        message = "Reset to 100"
    }
}

@Composable
fun SensitivityScreen(vm: SensitivityViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Sensitivity", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Converts your values between screen densities and packs them into a code.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("DPI")
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.fromDpi.toString(),
                    onValueChange = { vm.setFromDpi(it.toIntOrNull() ?: 400) },
                    label = { Text("Your dpi") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = vm.toDpi.toString(),
                    onValueChange = { vm.setToDpi(it.toIntOrNull() ?: 400) },
                    label = { Text("Target dpi") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.applyConversion() }, modifier = Modifier.fillMaxWidth()) {
                Text("Convert to target dpi")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Same physical rotation per centimetre needs sensitivity scaled inversely " +
                    "to dpi. Higher dpi means lower values.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("Values")
        Panel {
            SensValues.LABELS.forEachIndexed { i, label ->
                Text("$label: ${vm.values.toList()[i].toInt()}", fontSize = 11.sp)
                Slider(
                    value = vm.values.toList()[i],
                    onValueChange = { vm.setValue(i, it) },
                    valueRange = SensitivityMath.MIN..SensitivityMath.MAX,
                )
            }
            TextButton(onClick = { vm.reset() }) { Text("Reset all to 100") }
        }

        SectionTitle("Share code")
        Panel {
            Button(onClick = { vm.export() }, modifier = Modifier.fillMaxWidth()) {
                Text("Create code from my values")
            }
            if (vm.code.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(vm.code, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = vm.importText,
                onValueChange = { vm.setImportText(it) },
                label = { Text("Paste a code") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.doImport() }, modifier = Modifier.fillMaxWidth()) {
                Text("Load code")
            }
            vm.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
