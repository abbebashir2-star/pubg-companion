package ai.venice.pubgcompanion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import ai.venice.pubgcompanion.data.GameModeState
import ai.venice.pubgcompanion.system.ShizukuShell
import ai.venice.pubgcompanion.ui.screens.*
import ai.venice.pubgcompanion.ui.theme.CompanionTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        if (ShizukuShell.hasPermission()) GameModeState.note("Shizuku granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        setContent {
            CompanionTheme {
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
                                    label = { Text(d.label, fontSize = 10.sp) },
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
                        composable(Dest.Profiles.route) { ProfilesScreen() }
                        composable(Dest.Overlay.route) { OverlayScreen() }
                        composable(Dest.Network.route) { NetworkScreen() }
                        composable(Dest.Sensitivity.route) { SensitivityScreen() }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
        super.onDestroy()
    }
}
