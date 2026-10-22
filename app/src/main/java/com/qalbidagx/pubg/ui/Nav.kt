package com.qalbidagx.pubg.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Home", Icons.Filled.Speed),
    System("system", "System", Icons.Filled.Memory),
    Profiles("profiles", "Profiles", Icons.Filled.Tune),
    Overlay("overlay", "Aim", Icons.Filled.CenterFocusStrong),
    Network("network", "Net", Icons.Filled.Wifi),
    Sensitivity("sensitivity", "Sens", Icons.Filled.GpsFixed),
    Look("look", "Look", Icons.Filled.Palette),
}
