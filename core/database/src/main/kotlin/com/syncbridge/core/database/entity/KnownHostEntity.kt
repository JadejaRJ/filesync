package com.syncbridge.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A host key fingerprint SyncBridge has been told to trust for a given SFTP host+port. See `SftpKnownHostsStore`. */
@Entity(tableName = "known_hosts", indices = [Index(value = ["host", "port"], unique = true)])
data class KnownHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val fingerprint: String,
    val acceptedAt: Long,
)
