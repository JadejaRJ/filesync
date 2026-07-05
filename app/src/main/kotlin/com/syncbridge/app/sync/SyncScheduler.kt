package com.syncbridge.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.syncbridge.core.common.model.NetworkRule
import com.syncbridge.core.common.model.ScheduleRule
import com.syncbridge.core.common.model.SyncProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a [SyncProfile]'s [ScheduleRule] into a WorkManager [androidx.work.PeriodicWorkRequest].
 * Android/WorkManager caveats, documented rather than papered over:
 * - The minimum periodic interval WorkManager allows is 15 minutes; a profile's "Every 15 minutes"
 *   setting is the fastest truly periodic option available without a foreground service.
 * - There is no "battery above X%" constraint in WorkManager; [ScheduleRule.minBatteryPercent] maps to
 *   the OS-defined `setRequiresBatteryNotLow()` signal, which is the closest built-in equivalent.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun scheduleProfile(profile: SyncProfile) {
        if (!profile.isEnabled || profile.scheduleRule.kind == ScheduleRule.ScheduleKind.MANUAL_ONLY) {
            cancelProfile(profile.id)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkTypeFor(profile.networkRule))
            .setRequiresCharging(profile.scheduleRule.requireCharging)
            .apply {
                if (profile.scheduleRule.minBatteryPercent != null) setRequiresBatteryNotLow(true)
            }
            .build()

        val intervalMinutes = profile.scheduleRule.effectiveIntervalMinutes().coerceAtLeast(MIN_INTERVAL_MINUTES)

        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(SyncWorker.inputData(profile.id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(tagFor(profile.id))
            .build()

        workManager.enqueueUniquePeriodicWork(uniqueWorkName(profile.id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelProfile(profileId: Long) {
        workManager.cancelUniqueWork(uniqueWorkName(profileId))
    }

    /** Enqueues a single immediate run (used for "Sync now" when the caller doesn't need a foreground UI). */
    fun runOnce(profileId: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(SyncWorker.inputData(profileId))
            .addTag(tagFor(profileId))
            .build()
        workManager.enqueue(request)
    }

    private fun networkTypeFor(rule: NetworkRule): NetworkType = when (rule) {
        NetworkRule.WIFI_ONLY, NetworkRule.UNMETERED_ONLY, NetworkRule.WIFI_OR_ETHERNET -> NetworkType.UNMETERED
        NetworkRule.ANY_NETWORK -> NetworkType.CONNECTED
    }

    private fun uniqueWorkName(profileId: Long) = "sync_profile_$profileId"
    private fun tagFor(profileId: Long) = "profile_$profileId"

    companion object {
        private const val MIN_INTERVAL_MINUTES = 15L
    }
}
