package ai.venice.pubgcompanion.core

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

enum class PermissionKind(val title: String, val why: String) {
    USAGE_ACCESS("Usage access", "Lets the app see that PUBG started."),
    OVERLAY("Draw over other apps", "Needed for the stats overlay and crosshair."),
    DND("Do Not Disturb access", "Silences notifications during a match."),
    WRITE_SETTINGS("Modify system settings", "Brightness, touch sensitivity, timeout."),
    BATTERY("Ignore battery optimisation", "Otherwise Samsung kills the service."),
    NOTIFICATIONS("Notifications", "Lets the foreground service survive."),
}

object Permissions {

    fun isGranted(ctx: Context, kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.USAGE_ACCESS -> hasUsageAccess(ctx)
        PermissionKind.OVERLAY -> Settings.canDrawOverlays(ctx)
        PermissionKind.DND ->
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .isNotificationPolicyAccessGranted
        PermissionKind.WRITE_SETTINGS -> Settings.System.canWrite(ctx)
        PermissionKind.BATTERY ->
            (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
        PermissionKind.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            else true
    }

    fun intent(ctx: Context, kind: PermissionKind): Intent = when (kind) {
        PermissionKind.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        PermissionKind.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        )
        PermissionKind.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        PermissionKind.WRITE_SETTINGS -> Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${ctx.packageName}")
        )
        PermissionKind.BATTERY -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        )
        PermissionKind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    }

    fun missing(ctx: Context): List<PermissionKind> =
        PermissionKind.entries.filterNot { isGranted(ctx, it) }

    private fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
