package com.syncbridge.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted server connection. Enum-like columns ([protocol], [hostKeyVerificationMode]) are stored as
 * plain strings (the `.name` of their domain enum) rather than via a Room [androidx.room.TypeConverter],
 * so this module never needs to depend on `:core:common`'s enums directly — the `:app` repository layer
 * owns that mapping. [encryptedPassword] / [encryptedPrivateKey] / [encryptedPassphrase] are ciphertext
 * produced by `:core:security`'s Keystore-backed store; this table never sees plaintext secrets.
 */
@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val username: String,
    val encryptedPassword: String?,
    val encryptedPrivateKey: String?,
    val encryptedPassphrase: String?,
    val remoteRootPath: String,
    val timeoutSeconds: Int,
    val retryCount: Int,
    val keepAlive: Boolean,
    val hostKeyVerificationMode: String,
    val pinnedFingerprint: String?,
    val ftpPassiveMode: Boolean,
    val ftpEncoding: String,
    val useExplicitTls: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastTestStatus: String?,
    val lastTestTime: Long?,
    val lastSuccessfulLoginAt: Long?,
)
