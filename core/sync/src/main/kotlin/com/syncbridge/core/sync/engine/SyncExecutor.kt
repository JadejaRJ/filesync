package com.syncbridge.core.sync.engine

import com.syncbridge.core.common.client.LocalFileSystem
import com.syncbridge.core.common.client.RemoteFileClient
import com.syncbridge.core.common.error.AppError
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.DeleteRule
import com.syncbridge.core.common.model.FileFilterConfig
import com.syncbridge.core.common.model.SyncMode
import com.syncbridge.core.common.model.SyncOperationType
import com.syncbridge.core.common.model.SyncRunStatus
import com.syncbridge.core.common.util.PathValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/**
 * Executes a [SyncPlan] against a [LocalFileSystem] and [RemoteFileClient]: the only piece of the
 * engine that actually touches IO. Everything it decides *what* to do is delegated to [SyncPlanner]
 * and [ConflictResolver] beforehand, so this class is mostly plumbing: concurrency control, retry,
 * pause/cancel, per-file error mapping, and keeping the `sync_state` table (via
 * [SyncStateRepository]) in lockstep with what actually transferred.
 */
class SyncExecutor(
    private val localFileSystem: LocalFileSystem,
    private val remoteClient: RemoteFileClient,
    private val remoteRootPath: String,
    private val stateRepository: SyncStateRepository,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val maxParallelTransfers: Int = 4,
) {

    suspend fun execute(
        profileId: Long,
        mode: SyncMode,
        conflictRule: ConflictRule,
        deleteRule: DeleteRule,
        filter: FileFilterConfig,
        dryRun: Boolean = false,
        confirmedMassDeletion: Boolean = false,
        pauseController: PauseController = PauseController(),
        listener: SyncProgressListener = NoopListener,
    ): SyncRunSummary {
        listener.onScanStarted()

        if (!localFileSystem.hasValidPermission()) {
            throw AppError.LocalPermissionRevoked("profile:$profileId")
        }

        val local = localFileSystem.scan(filter)
        val remote = RemoteTreeScanner.scan(remoteClient, remoteRootPath, filter)
        val prior = stateRepository.getPriorState(profileId)

        val plan = SyncPlanner.plan(mode, conflictRule, deleteRule, local, remote, prior)
        listener.onPlanReady(plan)

        val filesScanned = (local.entriesByRelativePath.keys + remote.entriesByRelativePath.keys).size

        if (dryRun) {
            return SyncRunSummary(status = SyncRunStatus.SUCCESS, filesScanned = filesScanned, warnings = plan.warnings)
        }

        if (plan.warnings.isNotEmpty() && !confirmedMassDeletion) {
            return SyncRunSummary(status = SyncRunStatus.PAUSED, filesScanned = filesScanned, warnings = plan.warnings)
        }

        val accumulator = ResultAccumulator()
        val semaphore = Semaphore(maxParallelTransfers)
        var cancelled = false

        try {
            coroutineScope {
                for (op in plan.operations) {
                    launch {
                        semaphore.withPermit {
                            pauseController.awaitIfPaused()
                            currentCoroutineContext().ensureActive()
                            executeOneOperation(op, conflictRule, listener, accumulator, profileId)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            cancelled = true
        }

        val summary = accumulator.toSummary(
            status = when {
                cancelled -> SyncRunStatus.CANCELLED
                accumulator.errors.get() > 0 -> SyncRunStatus.PARTIAL_SUCCESS
                else -> SyncRunStatus.SUCCESS
            },
            filesScanned = filesScanned,
            warnings = plan.warnings,
        )
        listener.onRunCompleted(summary)
        return summary
    }

    private suspend fun executeOneOperation(
        op: SyncOperation,
        conflictRule: ConflictRule,
        listener: SyncProgressListener,
        accumulator: ResultAccumulator,
        profileId: Long,
    ) {
        if (op.type == SyncOperationType.CONFLICT) {
            handleConflict(op, conflictRule, listener, accumulator, profileId)
            return
        }

        listener.onOperationStarted(op)
        var attempt = 0
        while (true) {
            attempt++
            try {
                runOperationBody(op, profileId)
                accumulator.recordSuccess(op)
                listener.onOperationCompleted(op)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val appError = e as? AppError ?: AppError.Unknown(e.message, e)
                val willRetry = retryPolicy.shouldRetry(attempt, appError.retryable)
                listener.onOperationFailed(op, appError, attempt, willRetry)
                if (!willRetry) {
                    accumulator.recordError()
                    return
                }
                delay(retryPolicy.delayForAttempt(attempt))
            }
        }
    }

    private suspend fun runOperationBody(op: SyncOperation, profileId: Long) {
        val destinationFileName = op.destinationPath.substringAfterLast('/')
        if (PathValidator.hasInvalidFilenameCharacters(destinationFileName)) {
            throw AppError.InvalidFilename(op.destinationPath)
        }
        val sourceRemotePath = PathValidator.resolveSafe(remoteRootPath, op.relativePath)
        val destinationRemotePath = PathValidator.resolveSafe(remoteRootPath, op.destinationPath)

        when (op.type) {
            SyncOperationType.UPLOAD -> {
                ensureRemoteParentFolders(op.destinationPath)
                val size = op.localEntry?.size ?: (localFileSystem.sizeOf(op.relativePath) ?: 0)
                remoteClient.uploadFile(
                    localBytes = { localFileSystem.openInputStream(op.relativePath) },
                    sizeBytes = size,
                    remotePath = destinationRemotePath,
                )
                stateRepository.upsert(
                    profileId,
                    SyncStateEntry(
                        relativePath = op.destinationPath,
                        localSize = size,
                        localLastModified = op.localEntry?.lastModifiedEpochMillis,
                        remoteSize = size,
                        remoteLastModified = op.localEntry?.lastModifiedEpochMillis,
                    ),
                )
            }

            SyncOperationType.DOWNLOAD -> {
                ensureLocalParentFolders(op.destinationPath)
                val size = op.remoteEntry?.size ?: 0
                remoteClient.downloadFile(
                    remotePath = sourceRemotePath,
                    localSink = { localFileSystem.openOutputStream(op.destinationPath, size) },
                )
                stateRepository.upsert(
                    profileId,
                    SyncStateEntry(
                        relativePath = op.destinationPath,
                        localSize = size,
                        localLastModified = op.remoteEntry?.lastModifiedEpochMillis,
                        remoteSize = size,
                        remoteLastModified = op.remoteEntry?.lastModifiedEpochMillis,
                    ),
                )
            }

            SyncOperationType.DELETE_LOCAL -> {
                localFileSystem.delete(op.relativePath, moveToRecycleBin = op.reason.contains("recycle bin"))
                stateRepository.remove(profileId, op.relativePath)
            }

            SyncOperationType.DELETE_REMOTE -> {
                remoteClient.deleteFile(sourceRemotePath)
                stateRepository.remove(profileId, op.relativePath)
            }

            SyncOperationType.CREATE_LOCAL_FOLDER -> localFileSystem.createDirectories(op.relativePath)
            SyncOperationType.CREATE_REMOTE_FOLDER -> remoteClient.createFolder(sourceRemotePath)
            SyncOperationType.SKIP, SyncOperationType.CONFLICT -> Unit
        }
    }

    private suspend fun handleConflict(
        op: SyncOperation,
        conflictRule: ConflictRule,
        listener: SyncProgressListener,
        accumulator: ResultAccumulator,
        profileId: Long,
    ) {
        val info = op.conflictInfo ?: return
        val resolution = ConflictResolver.resolve(info, conflictRule, LocalDate.now().toString())
        when (resolution) {
            is ConflictResolution.RequiresUserInput -> {
                accumulator.recordConflict()
                listener.onOperationSkipped(op, "Awaiting user decision")
            }
            is ConflictResolution.Skip -> {
                accumulator.recordConflict()
                listener.onOperationSkipped(op, "Skipped per conflict rule")
            }
            is ConflictResolution.KeepLocal -> {
                executeOneOperation(op.copy(type = SyncOperationType.UPLOAD, reason = "Conflict resolved: keep local"), conflictRule, listener, accumulator, profileId)
            }
            is ConflictResolution.KeepRemote -> {
                executeOneOperation(op.copy(type = SyncOperationType.DOWNLOAD, reason = "Conflict resolved: keep remote"), conflictRule, listener, accumulator, profileId)
            }
            is ConflictResolution.KeepBoth -> {
                // Source path (op.relativePath) is unchanged on each side; only the destination differs,
                // so the local version lands on remote under a new name and vice versa, leaving both
                // originals untouched. See SyncOperation.destinationPath.
                val uploadOp = op.copy(
                    type = SyncOperationType.UPLOAD,
                    destinationPathOverride = resolution.localRenamedPath,
                    reason = "Conflict resolved: keep both (local copy)",
                )
                val downloadOp = op.copy(
                    type = SyncOperationType.DOWNLOAD,
                    destinationPathOverride = resolution.remoteRenamedPath,
                    reason = "Conflict resolved: keep both (remote copy)",
                )
                executeOneOperation(uploadOp, conflictRule, listener, accumulator, profileId)
                executeOneOperation(downloadOp, conflictRule, listener, accumulator, profileId)
            }
        }
    }

    private suspend fun ensureRemoteParentFolders(relativePath: String) {
        val dir = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        if (dir.isEmpty()) return
        val absolute = PathValidator.resolveSafe(remoteRootPath, dir)
        remoteClient.createFolder(absolute)
    }

    private suspend fun ensureLocalParentFolders(relativePath: String) {
        val dir = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        if (dir.isEmpty()) return
        localFileSystem.createDirectories(dir)
    }

    private object NoopListener : SyncProgressListener

    private class ResultAccumulator {
        val uploaded = java.util.concurrent.atomic.AtomicInteger()
        val downloaded = java.util.concurrent.atomic.AtomicInteger()
        val deleted = java.util.concurrent.atomic.AtomicInteger()
        val conflicted = java.util.concurrent.atomic.AtomicInteger()
        val errors = java.util.concurrent.atomic.AtomicInteger()
        val bytesUploaded = java.util.concurrent.atomic.AtomicLong()
        val bytesDownloaded = java.util.concurrent.atomic.AtomicLong()
        private val mutex = Mutex()

        suspend fun recordSuccess(op: SyncOperation) = mutex.withLock {
            when (op.type) {
                SyncOperationType.UPLOAD -> {
                    uploaded.incrementAndGet()
                    bytesUploaded.addAndGet(op.localEntry?.size ?: 0)
                }
                SyncOperationType.DOWNLOAD -> {
                    downloaded.incrementAndGet()
                    bytesDownloaded.addAndGet(op.remoteEntry?.size ?: 0)
                }
                SyncOperationType.DELETE_LOCAL, SyncOperationType.DELETE_REMOTE -> deleted.incrementAndGet()
                else -> Unit
            }
        }

        fun recordError() {
            errors.incrementAndGet()
        }

        fun recordConflict() {
            conflicted.incrementAndGet()
        }

        fun toSummary(status: SyncRunStatus, filesScanned: Int, warnings: List<PlanWarning>) = SyncRunSummary(
            status = status,
            filesScanned = filesScanned,
            filesUploaded = uploaded.get(),
            filesDownloaded = downloaded.get(),
            filesSkipped = 0,
            filesDeleted = deleted.get(),
            filesConflicted = conflicted.get(),
            errorsCount = errors.get(),
            bytesUploaded = bytesUploaded.get(),
            bytesDownloaded = bytesDownloaded.get(),
            warnings = warnings,
        )
    }
}
