package com.syncbridge.app.data.repository

import com.syncbridge.core.common.error.AppError
import com.syncbridge.core.common.model.SyncRunStatus
import com.syncbridge.core.database.dao.SyncErrorDao
import com.syncbridge.core.database.dao.SyncRunDao
import com.syncbridge.core.database.entity.SyncErrorEntity
import com.syncbridge.core.database.entity.SyncRunEntity
import com.syncbridge.core.sync.engine.SyncRunSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncHistoryRepository @Inject constructor(
    private val runDao: SyncRunDao,
    private val errorDao: SyncErrorDao,
) {
    fun observeRunsForProfile(profileId: Long): Flow<List<SyncRunEntity>> = runDao.observeForProfile(profileId)

    fun observeErrorsForProfile(profileId: Long): Flow<List<SyncErrorEntity>> = errorDao.observeForProfile(profileId)

    suspend fun startRun(profileId: Long): Long = runDao.insert(
        SyncRunEntity(
            profileId = profileId,
            startedAt = System.currentTimeMillis(),
            completedAt = null,
            status = SyncRunStatus.RUNNING.name,
            filesScanned = 0,
            filesUploaded = 0,
            filesDownloaded = 0,
            filesSkipped = 0,
            filesDeleted = 0,
            filesConflicted = 0,
            errorsCount = 0,
            bytesUploaded = 0,
            bytesDownloaded = 0,
        ),
    )

    suspend fun completeRun(runId: Long, profileId: Long, summary: SyncRunSummary) {
        runDao.update(
            SyncRunEntity(
                id = runId,
                profileId = profileId,
                startedAt = runDao.getById(runId)?.startedAt ?: System.currentTimeMillis(),
                completedAt = System.currentTimeMillis(),
                status = summary.status.name,
                filesScanned = summary.filesScanned,
                filesUploaded = summary.filesUploaded,
                filesDownloaded = summary.filesDownloaded,
                filesSkipped = summary.filesSkipped,
                filesDeleted = summary.filesDeleted,
                filesConflicted = summary.filesConflicted,
                errorsCount = summary.errorsCount,
                bytesUploaded = summary.bytesUploaded,
                bytesDownloaded = summary.bytesDownloaded,
            ),
        )
    }

    suspend fun recordError(runId: Long, profileId: Long, filePath: String, operation: String, error: AppError) {
        errorDao.insert(
            SyncErrorEntity(
                runId = runId,
                profileId = profileId,
                filePath = filePath,
                operation = operation,
                errorCode = error.code,
                errorMessage = error.userMessage,
                suggestedFix = error.suggestedFix,
                retryable = error.retryable,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearHistory(profileId: Long) {
        runDao.clearForProfile(profileId)
        errorDao.clearForProfile(profileId)
    }
}
