package com.syncbridge.core.sync.engine

import com.syncbridge.core.common.model.SyncRunStatus

/** Live callbacks the UI (Live Sync screen) and foreground-service notification subscribe to. */
interface SyncProgressListener {
    fun onScanStarted() {}
    fun onPlanReady(plan: SyncPlan) {}
    fun onOperationStarted(op: SyncOperation) {}
    fun onOperationProgress(op: SyncOperation, bytesTransferred: Long, totalBytes: Long) {}
    fun onOperationCompleted(op: SyncOperation) {}
    fun onOperationFailed(op: SyncOperation, error: Throwable, attempt: Int, willRetry: Boolean) {}
    fun onOperationSkipped(op: SyncOperation, reason: String) {}
    fun onRunCompleted(summary: SyncRunSummary) {}
}

data class SyncRunSummary(
    val status: SyncRunStatus,
    val filesScanned: Int = 0,
    val filesUploaded: Int = 0,
    val filesDownloaded: Int = 0,
    val filesSkipped: Int = 0,
    val filesDeleted: Int = 0,
    val filesConflicted: Int = 0,
    val errorsCount: Int = 0,
    val bytesUploaded: Long = 0,
    val bytesDownloaded: Long = 0,
    val warnings: List<PlanWarning> = emptyList(),
)
