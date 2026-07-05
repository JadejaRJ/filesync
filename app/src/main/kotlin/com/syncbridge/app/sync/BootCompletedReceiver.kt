package com.syncbridge.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.syncbridge.app.data.repository.SyncProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WorkManager's own periodic-work bookkeeping already survives a reboot on its own; this receiver is
 * a defensive re-sync of schedules (some OEM battery managers are known to clear WorkManager state
 * more aggressively than stock Android), not something the app depends on for correctness.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var syncProfileRepository: SyncProfileRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncProfileRepository.getEnabledProfiles().forEach(syncScheduler::scheduleProfile)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
