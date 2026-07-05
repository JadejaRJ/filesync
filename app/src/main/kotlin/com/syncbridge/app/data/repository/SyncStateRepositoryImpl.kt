package com.syncbridge.app.data.repository

import com.syncbridge.core.database.dao.SyncStateDao
import com.syncbridge.core.database.entity.SyncStateEntity as SyncStateRow
import com.syncbridge.core.sync.engine.SyncStateEntry
import com.syncbridge.core.sync.engine.SyncStateRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges `:core:sync`'s [SyncStateRepository] contract to Room's [SyncStateDao]. */
@Singleton
class SyncStateRepositoryImpl @Inject constructor(
    private val dao: SyncStateDao,
) : SyncStateRepository {

    override suspend fun getPriorState(profileId: Long): Map<String, SyncStateEntry> =
        dao.getAllForProfile(profileId).associate { row ->
            row.relativePath to SyncStateEntry(
                relativePath = row.relativePath,
                localSize = row.localSize,
                localLastModified = row.localLastModified,
                remoteSize = row.remoteSize,
                remoteLastModified = row.remoteLastModified,
                checksum = row.checksum,
            )
        }

    override suspend fun upsert(profileId: Long, entry: SyncStateEntry) {
        dao.upsert(
            SyncStateRow(
                profileId = profileId,
                relativePath = entry.relativePath,
                localSize = entry.localSize,
                localLastModified = entry.localLastModified,
                remoteSize = entry.remoteSize,
                remoteLastModified = entry.remoteLastModified,
                checksum = entry.checksum,
                lastSyncedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun remove(profileId: Long, relativePath: String) {
        dao.delete(profileId, relativePath)
    }
}
