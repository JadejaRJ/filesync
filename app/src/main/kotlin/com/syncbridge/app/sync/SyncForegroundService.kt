package com.syncbridge.app.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for a single active/manual sync run, per spec section 13: persistent
 * notification with pause/resume/cancel while the app is actively transferring files. Scheduled
 * background syncs (see [SyncWorker]) run without this service unless the user brings one to the
 * foreground explicitly (e.g. tapping "Sync now" from the dashboard).
 */
@AndroidEntryPoint
class SyncForegroundService : Service() {

    @Inject lateinit var syncRunner: SyncRunner

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runJob: Job? = null
    private var progressObserverJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileId = intent?.getLongExtra(EXTRA_PROFILE_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_START -> if (profileId != -1L) startSync(profileId)
            ACTION_PAUSE_RESUME -> if (profileId != -1L) togglePause(profileId)
            ACTION_CANCEL -> {
                runJob?.cancel()
                stopSelfCleanly()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSync(profileId: Long) {
        if (runJob?.isActive == true) return

        // startForeground() must be called synchronously within onStartCommand's call stack (Android
        // requires it within a few seconds of startForegroundService()); the profile name is filled
        // in moments later once the first progress update arrives.
        startForeground(NotificationHelper.SYNC_NOTIFICATION_ID, NotificationHelper.buildInitialSyncNotification(this, "Sync"))

        progressObserverJob = serviceScope.launch {
            syncRunner.liveProgress.collect { state ->
                if (state == null) return@collect
                val notification = NotificationHelper.buildProgressNotification(
                    context = this@SyncForegroundService,
                    state = state,
                    pauseResumeIntent = NotificationHelper.pendingIntentFor(this@SyncForegroundService, ACTION_PAUSE_RESUME, profileId),
                    cancelIntent = NotificationHelper.pendingIntentFor(this@SyncForegroundService, ACTION_CANCEL, profileId),
                )
                NotificationManagerCompat.from(this@SyncForegroundService).notify(NotificationHelper.SYNC_NOTIFICATION_ID, notification)
            }
        }

        runJob = serviceScope.launch {
            try {
                val summary = syncRunner.runProfile(profileId)
                if (summary.errorsCount > 0) {
                    NotificationHelper.showAlert(
                        this@SyncForegroundService,
                        "Sync finished with errors",
                        "${summary.errorsCount} file(s) failed to sync. Open SyncBridge for details.",
                        profileId,
                    )
                } else if (summary.warnings.isNotEmpty()) {
                    NotificationHelper.showAlert(
                        this@SyncForegroundService,
                        "Sync paused for confirmation",
                        "A large number of deletions were detected. Open SyncBridge to review and confirm.",
                        profileId,
                    )
                }
            } finally {
                stopSelfCleanly()
            }
        }
    }

    private fun togglePause(profileId: Long) {
        val isPaused = syncRunner.liveProgress.value?.isPaused == true
        if (isPaused) syncRunner.resume(profileId) else syncRunner.pause(profileId)
    }

    private fun stopSelfCleanly() {
        progressObserverJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.syncbridge.app.sync.action.START"
        const val ACTION_PAUSE_RESUME = "com.syncbridge.app.sync.action.PAUSE_RESUME"
        const val ACTION_CANCEL = "com.syncbridge.app.sync.action.CANCEL"
        const val EXTRA_PROFILE_ID = "profile_id"

        fun start(context: Context, profileId: Long) {
            val intent = Intent(context, SyncForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE_ID, profileId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, profileId: Long) {
            context.startService(
                Intent(context, SyncForegroundService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_PROFILE_ID, profileId),
            )
        }
    }
}
