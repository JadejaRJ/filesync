package com.syncbridge.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.syncbridge.app.R

object NotificationHelper {
    const val CHANNEL_SYNC = "syncbridge_sync_channel"
    const val CHANNEL_ALERTS = "syncbridge_alerts_channel"
    const val SYNC_NOTIFICATION_ID = 1001
    private const val ALERT_NOTIFICATION_ID_BASE = 2000

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SYNC, "Sync activity", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing file sync progress"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Sync alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Sync completed, failed, conflicts, and safety warnings"
            },
        )
    }

    fun buildInitialSyncNotification(context: Context, profileName: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setContentTitle(profileName)
            .setContentText("Preparing to sync…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun buildProgressNotification(
        context: Context,
        state: LiveSyncState,
        pauseResumeIntent: PendingIntent,
        cancelIntent: PendingIntent,
    ): android.app.Notification {
        val fileLabel = state.currentFile?.substringAfterLast('/') ?: ""
        val text = "${state.currentOperationLabel} $fileLabel (${state.filesCompleted}/${state.totalOperations})" +
            if (state.errors > 0) " · ${state.errors} error(s)" else ""
        return NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setContentTitle(state.profileName)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(state.totalOperations.coerceAtLeast(1), state.filesCompleted, state.totalOperations == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (state.isPaused) "Resume" else "Pause", pauseResumeIntent)
            .addAction(0, "Cancel", cancelIntent)
            .build()
    }

    fun showAlert(context: Context, title: String, text: String, profileId: Long = 0) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
        androidx.core.app.NotificationManagerCompat.from(context)
            .notify(ALERT_NOTIFICATION_ID_BASE + profileId.toInt(), notification)
    }

    fun pendingIntentFor(context: Context, action: String, profileId: Long): PendingIntent {
        val intent = Intent(context, SyncForegroundService::class.java).setAction(action).putExtra(SyncForegroundService.EXTRA_PROFILE_ID, profileId)
        return PendingIntent.getService(
            context,
            (action + profileId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
