package com.qalbidagx.pubg.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.qalbidagx.pubg.data.GameModeState
import com.qalbidagx.pubg.data.SettingsStore
import com.qalbidagx.pubg.service.RecorderService
import com.qalbidagx.pubg.system.ShizukuShell
import com.qalbidagx.pubg.ui.screens.*
import com.qalbidagx.pubg.ui.theme.CompanionTheme
import rikka.shizuku.Shizuku

object RecorderController {
    var start: (() -> Unit)? = null
    var stop: (() -> Unit)? = null
}

class MainActivity : ComponentActivity() {

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        if (ShizukuShell.hasPermission()) GameModeState.note("Shizuku granted")
    }

    private var pendingAudio = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val i = Intent(this, RecorderService::class.java).apply {
                putExtra(RecorderService.EXTRA_CODE, result.resultCode)
                putExtra(RecorderService.EXTRA_DATA, data)
                putExtra(RecorderService.EXTRA_AUDIO, pendingAudio)
            }
            ContextCompat.startForegroundService(this, i)
        }
    }

    private val audioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingAudio = granted
        launchProjection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        RecorderController.start = {
            val hasAudio = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (hasAudio) {
                pendingAudio = true
                launchProjection()
            } else {
                audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        RecorderController.stop = {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP),
            )
        }

        setContent {
            val settings = remember { SettingsStore(applicationContext) }
            val accent by settings.accentColor.collectAsStateWithLifecycle(
                initialValue = 0xFFFF2A3C.toInt()
            )

            CompanionTheme(accent = Color(accent)) {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val current = entry?.destination?.route

                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            Dest.entries.forEach { d ->
                                NavigationBarItem(
                                    selected = current == d.route,
                                    onClick = {
                                        nav.navigate(d.route) {
                                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(d.icon, contentDescription = d.label) },
                                    label = { Text(d.label, fontSize = 9.sp) },
                                )
                            }
                        }
                    }
                ) { pad ->
                    NavHost(
                        nav,
                        startDestination = Dest.Dashboard.route,
                        modifier = Modifier.padding(pad),
                    ) {
                        composable(Dest.Dashboard.route) { DashboardScreen() }
                        composable(Dest.System.route) { SystemScreen() }
                        composable(Dest.Profiles.route) { ProfilesScreen() }
                        composable(Dest.Overlay.route) { OverlayScreen() }
                        composable(Dest.Network.route) { NetworkScreen() }
                        composable(Dest.Sensitivity.route) { SensitivityScreen() }
                        composable(Dest.Look.route) { LookScreen() }
                    }
                }
            }
        }
    }

    private fun launchProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
        RecorderController.start = null
        RecorderController.stop = null
        super.onDestroy()
    }
}
