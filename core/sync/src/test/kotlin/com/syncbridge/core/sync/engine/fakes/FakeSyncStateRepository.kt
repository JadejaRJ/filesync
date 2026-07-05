package com.syncbridge.core.sync.engine.fakes

import com.syncbridge.core.sync.engine.SyncStateEntry
import com.syncbridge.core.sync.engine.SyncStateRepository

class FakeSyncStateRepository(
    initial: Map<String, SyncStateEntry> = emptyMap(),
) : SyncStateRepository {
    val state = initial.toMutableMap()

    override suspend fun getPriorState(profileId: Long): Map<String, SyncStateEntry> = state.toMap()

    override suspend fun upsert(profileId: Long, entry: SyncStateEntry) {
        state[entry.relativePath] = entry
    }

    override suspend fun remove(profileId: Long, relativePath: String) {
        state.remove(relativePath)
    }
}
