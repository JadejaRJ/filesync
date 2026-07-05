package com.syncbridge.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.syncbridge.app.data.repository.SyncProfileRepository
import com.syncbridge.core.common.model.SyncRunStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** WorkManager-scheduled sync for one profile (see [SyncScheduler]). Retries with WorkManager's own
 * exponential backoff on unexpected failure; a paused-for-confirmation or cancelled run is *not*
 * treated as a worker failure since it isn't something retrying will fix. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRunner: SyncRunner,
    private val syncProfileRepository: SyncProfileRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getLong(KEY_PROFILE_ID, -1L)
        if (profileId == -1L) return Result.failure()

        val profile = syncProfileRepository.getById(profileId) ?: return Result.failure()
        if (!profile.isEnabled) return Result.success()

        return try {
            val summary = syncRunner.runProfile(profileId)
            if (summary.status == SyncRunStatus.PAUSED) {
                NotificationHelper.showAlert(
                    applicationContext,
                    "Sync paused for confirmation",
                    "\"${profile.name}\" would delete an unusually large number of files. Open SyncBridge to review.",
                    profileId,
                )
            }
            when (summary.status) {
                SyncRunStatus.FAILED -> Result.retry()
                else -> Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"

        fun inputData(profileId: Long) = workDataOf(KEY_PROFILE_ID to profileId)
    }
}
