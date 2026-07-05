package com.syncbridge.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A file moved aside instead of deleted outright (see `DeleteRule.RECYCLE_BIN`). [side] is "LOCAL" or
 * "REMOTE". [storedLocation] is either a SAF URI (local side) or a remote path under a hidden
 * `.syncbridge-recycle-bin/` folder (remote side) where the original bytes were preserved.
 */
@Entity(tableName = "recycle_bin", indices = [Index("profileId")])
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val originalRelativePath: String,
    val side: String,
    val storedLocation: String,
    val deletedAt: Long,
    val restorable: Boolean = true,
)
