package com.syncbridge.app.data.repository

import com.syncbridge.app.data.mapper.FilterConfigJson
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.DeleteRule
import com.syncbridge.core.common.model.NetworkRule
import com.syncbridge.core.common.model.ScheduleRule
import com.syncbridge.core.common.model.SyncMode
import com.syncbridge.core.common.model.SyncProfile
import com.syncbridge.core.common.model.SyncRunStatus
import com.syncbridge.core.database.dao.SyncProfileDao
import com.syncbridge.core.database.entity.SyncProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private fun SyncProfileEntity.toDomain(): SyncProfile = SyncProfile(
    id = id,
    name = name,
    localUri = localUri,
    remoteConnectionId = remoteConnectionId,
    remotePath = remotePath,
    syncMode = SyncMode.valueOf(syncMode),
    conflictRule = ConflictRule.valueOf(conflictRule),
    deleteRule = DeleteRule.valueOf(deleteRule),
    scheduleRule = ScheduleRule(
        kind = ScheduleRule.ScheduleKind.valueOf(scheduleKind),
        intervalMinutes = scheduleIntervalMinutes,
        requireCharging = requireCharging,
        requireWifi = requireWifi,
        minBatteryPercent = minBatteryPercent,
    ),
    networkRule = NetworkRule.valueOf(networkRule),
    isRealtimeEnabled = isRealtimeEnabled,
    isEnabled = isEnabled,
    filterConfig = FilterConfigJson.fromJson(filterConfigJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncAt = lastSyncAt,
    lastSyncStatus = lastSyncStatus?.let { runCatching { SyncRunStatus.valueOf(it) }.getOrNull() },
    totalFilesSynced = totalFilesSynced,
    totalUploadBytes = totalUploadBytes,
    totalDownloadBytes = totalDownloadBytes,
    totalErrors = totalErrors,
)

private fun SyncProfile.toEntity(): SyncProfileEntity = SyncProfileEntity(
    id = id,
    name = name,
    localUri = localUri,
    remoteConnectionId = remoteConnectionId,
    remotePath = remotePath,
    syncMode = syncMode.name,
    conflictRule = conflictRule.name,
    deleteRule = deleteRule.name,
    scheduleKind = scheduleRule.kind.name,
    scheduleIntervalMinutes = scheduleRule.intervalMinutes,
    requireCharging = scheduleRule.requireCharging,
    requireWifi = scheduleRule.requireWifi,
    minBatteryPercent = scheduleRule.minBatteryPercent,
    networkRule = networkRule.name,
    isRealtimeEnabled = isRealtimeEnabled,
    isEnabled = isEnabled,
    filterConfigJson = FilterConfigJson.toJson(filterConfig),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncAt = lastSyncAt,
    lastSyncStatus = lastSyncStatus?.name,
    totalFilesSynced = totalFilesSynced,
    totalUploadBytes = totalUploadBytes,
    totalDownloadBytes = totalDownloadBytes,
    totalErrors = totalErrors,
)

@Singleton
class SyncProfileRepository @Inject constructor(
    private val dao: SyncProfileDao,
) {
    fun observeAll(): Flow<List<SyncProfile>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: Long): Flow<SyncProfile?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): SyncProfile? = dao.getById(id)?.toDomain()

    suspend fun getEnabledProfiles(): List<SyncProfile> = dao.getEnabled().map { it.toDomain() }

    suspend fun save(profile: SyncProfile): Long {
        val now = System.currentTimeMillis()
        val entity = profile.toEntity().copy(
            createdAt = if (profile.id == 0L) now else profile.createdAt,
            updatedAt = now,
        )
        return if (profile.id == 0L) dao.insert(entity) else { dao.update(entity); entity.id }
    }

    suspend fun delete(profile: SyncProfile) = dao.delete(profile.toEntity())

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled, System.currentTimeMillis())

    suspend fun recordSyncResult(
        id: Long,
        status: SyncRunStatus,
        filesSynced: Long,
        uploadBytes: Long,
        downloadBytes: Long,
        errors: Long,
    ) = dao.recordSyncResult(id, System.currentTimeMillis(), status.name, filesSynced, uploadBytes, downloadBytes, errors)
}
