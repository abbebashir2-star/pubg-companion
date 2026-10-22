package ai.venice.pubgcompanion.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Filled.Speed),
    Profiles("profiles", "Profiles", Icons.Filled.Tune),
    Overlay("overlay", "Overlay", Icons.Filled.Layers),
    Network("network", "Network", Icons.Filled.Wifi),
    Sensitivity("sensitivity", "Aim", Icons.Filled.GpsFixed),
}
