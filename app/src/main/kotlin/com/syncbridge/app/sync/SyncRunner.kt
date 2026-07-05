package com.syncbridge.app.sync

import com.syncbridge.app.data.local.SafLocalFileSystemFactory
import com.syncbridge.app.data.remote.RemoteClientFactory
import com.syncbridge.app.data.repository.ConnectionRepository
import com.syncbridge.app.data.repository.SyncHistoryRepository
import com.syncbridge.app.data.repository.SyncProfileRepository
import com.syncbridge.app.di.ApplicationScope
import com.syncbridge.core.common.error.AppError
import com.syncbridge.core.common.model.SyncOperationType
import com.syncbridge.core.common.model.SyncRunStatus
import com.syncbridge.core.sync.engine.PauseController
import com.syncbridge.core.sync.engine.SyncExecutor
import com.syncbridge.core.sync.engine.SyncOperation
import com.syncbridge.core.sync.engine.SyncPlan
import com.syncbridge.core.sync.engine.SyncProgressListener
import com.syncbridge.core.sync.engine.SyncRunSummary
import com.syncbridge.core.sync.engine.SyncStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one sync profile end to end (scan -> plan -> execute) using [SyncExecutor], and is the single
 * place [com.syncbridge.app.sync.SyncWorker] (scheduled/background) and
 * [com.syncbridge.app.sync.SyncForegroundService] (manual/active) both go through, so live progress,
 * history, and error persistence behave identically regardless of what triggered the run.
 */
@Singleton
class SyncRunner @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val syncProfileRepository: SyncProfileRepository,
    private val syncStateRepository: SyncStateRepository,
    private val syncHistoryRepository: SyncHistoryRepository,
    private val remoteClientFactory: RemoteClientFactory,
    private val localFileSystemFactory: SafLocalFileSystemFactory,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _liveProgress = MutableStateFlow<LiveSyncState?>(null)
    val liveProgress: StateFlow<LiveSyncState?> = _liveProgress.asStateFlow()

    private val pauseControllers = mutableMapOf<Long, PauseController>()

    fun pause(profileId: Long) {
        pauseControllers[profileId]?.pause()
        _liveProgress.update { if (it?.profileId == profileId) it.copy(isPaused = true) else it }
    }

    fun resume(profileId: Long) {
        pauseControllers[profileId]?.resume()
        _liveProgress.update { if (it?.profileId == profileId) it.copy(isPaused = false) else it }
    }

    suspend fun runProfile(
        profileId: Long,
        dryRun: Boolean = false,
        confirmedMassDeletion: Boolean = false,
    ): SyncRunSummary {
        val profile = syncProfileRepository.getById(profileId)
            ?: return SyncRunSummary(status = SyncRunStatus.FAILED, errorsCount = 1)
        val connectionConfig = connectionRepository.getConfig(profile.remoteConnectionId)
            ?: return SyncRunSummary(status = SyncRunStatus.FAILED, errorsCount = 1)

        val localFileSystem = localFileSystemFactory.create(profile.localUri)
        val remoteClient = remoteClientFactory.create(connectionConfig)
        val pauseController = PauseController().also { pauseControllers[profileId] = it }
        val runId = syncHistoryRepository.startRun(profileId)

        _liveProgress.value = LiveSyncState(
            profileId = profileId,
            profileName = profile.name,
            currentOperationLabel = "Scanning",
            totalOperations = 0,
        )

        val listener = object : SyncProgressListener {
            override fun onPlanReady(plan: SyncPlan) {
                _liveProgress.update {
                    it?.copy(
                        currentOperationLabel = "Syncing",
                        totalOperations = plan.operations.size,
                        awaitingMassDeletionConfirmation = plan.requiresUserConfirmation && !confirmedMassDeletion,
                    )
                }
            }

            override fun onOperationStarted(op: SyncOperation) {
                _liveProgress.update { it?.copy(currentFile = op.relativePath, currentFileBytesDone = 0, currentFileBytesTotal = 0) }
            }

            override fun onOperationProgress(op: SyncOperation, bytesTransferred: Long, totalBytes: Long) {
                _liveProgress.update { it?.copy(currentFileBytesDone = bytesTransferred, currentFileBytesTotal = totalBytes) }
            }

            override fun onOperationCompleted(op: SyncOperation) {
                _liveProgress.update {
                    it?.copy(
                        filesCompleted = it.filesCompleted + 1,
                        bytesUploaded = it.bytesUploaded + (if (op.type == SyncOperationType.UPLOAD) op.localEntry?.size ?: 0 else 0),
                        bytesDownloaded = it.bytesDownloaded + (if (op.type == SyncOperationType.DOWNLOAD) op.remoteEntry?.size ?: 0 else 0),
                    )
                }
            }

            override fun onOperationFailed(op: SyncOperation, error: Throwable, attempt: Int, willRetry: Boolean) {
                Timber.w(error, "Sync operation failed for %s (attempt %d, willRetry=%s)", op.relativePath, attempt, willRetry)
                if (!willRetry) {
                    _liveProgress.update { it?.copy(errors = it.errors + 1) }
                    val appError = error as? AppError ?: AppError.Unknown(error.message, error)
                    appScope.launch {
                        syncHistoryRepository.recordError(runId, profileId, op.relativePath, op.type.name, appError)
                    }
                }
            }

            override fun onOperationSkipped(op: SyncOperation, reason: String) {
                Timber.i("Skipped %s: %s", op.relativePath, reason)
            }
        }

        val executor = SyncExecutor(
            localFileSystem = localFileSystem,
            remoteClient = remoteClient,
            remoteRootPath = connectionConfig.remoteRootPath,
            stateRepository = syncStateRepository,
            retryPolicy = com.syncbridge.core.sync.engine.RetryPolicy(maxAttempts = connectionConfig.retryCount.coerceAtLeast(1)),
        )

        val summary = try {
            remoteClient.connect()
            executor.execute(
                profileId = profileId,
                mode = profile.syncMode,
                conflictRule = profile.conflictRule,
                deleteRule = profile.deleteRule,
                filter = profile.filterConfig,
                dryRun = dryRun,
                confirmedMassDeletion = confirmedMassDeletion,
                pauseController = pauseController,
                listener = listener,
            )
        } catch (e: AppError) {
            syncHistoryRepository.recordError(runId, profileId, "", "CONNECT", e)
            SyncRunSummary(status = SyncRunStatus.FAILED, errorsCount = 1)
        } finally {
            runCatching { remoteClient.disconnect() }
            pauseControllers.remove(profileId)
        }

        syncHistoryRepository.completeRun(runId, profileId, summary)
        syncProfileRepository.recordSyncResult(
            id = profileId,
            status = summary.status,
            filesSynced = (summary.filesUploaded + summary.filesDownloaded).toLong(),
            uploadBytes = summary.bytesUploaded,
            downloadBytes = summary.bytesDownloaded,
            errors = summary.errorsCount.toLong(),
        )
        _liveProgress.value = null
        return summary
    }
}
