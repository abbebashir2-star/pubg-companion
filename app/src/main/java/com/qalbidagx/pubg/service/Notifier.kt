package com.qalbidagx.pubg.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.qalbidagx.pubg.R

object Notifier {

    const val CH_GAME = "game_mode"
    const val CH_OVERLAY = "overlay"
    const val CH_RECORD = "record"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_GAME, "Game mode", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_OVERLAY, "Overlay", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RECORD, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    fun build(ctx: Context, channel: String, title: String, text: String): Notification =
        NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_companion)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun recording(ctx: Context): Notification {
        val stop = PendingIntent.getService(
            ctx, 11,
            Intent(ctx, RecorderService::class.java).setAction(RecorderService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CH_RECORD)
            .setSmallIcon(R.drawable.ic_stat_companion)
            .setContentTitle("QalbiDagx is recording")
            .setContentText("Tap the floating button to stop")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stop)
            .build()
    }
}
