package com.syncbridge.core.sync.engine

/** Reads/writes the persisted `sync_state` table (Room-backed in `:core:database`) for one profile. */
interface SyncStateRepository {
    suspend fun getPriorState(profileId: Long): Map<String, SyncStateEntry>
    suspend fun upsert(profileId: Long, entry: SyncStateEntry)
    suspend fun remove(profileId: Long, relativePath: String)
}
