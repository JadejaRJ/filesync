package com.syncbridge.app.data.repository

import com.syncbridge.core.common.model.ConnectionConfig
import com.syncbridge.core.common.model.HostKeyVerification
import com.syncbridge.core.common.model.ProtocolType
import com.syncbridge.core.database.dao.ConnectionDao
import com.syncbridge.core.database.entity.ConnectionEntity
import com.syncbridge.core.security.CredentialCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Lightweight, secret-free view of a connection for list screens (Saved Connections, profile pickers). */
data class ConnectionSummary(
    val id: Long,
    val name: String,
    val protocol: ProtocolType,
    val host: String,
    val port: Int,
    val isInsecure: Boolean,
    val lastTestStatus: String?,
    val lastTestTime: Long?,
    val lastSuccessfulLoginAt: Long?,
)

private fun ConnectionEntity.toSummary() = ConnectionSummary(
    id = id,
    name = name,
    protocol = ProtocolType.valueOf(protocol),
    host = host,
    port = port,
    isInsecure = protocol == ProtocolType.FTP.name && !useExplicitTls,
    lastTestStatus = lastTestStatus,
    lastTestTime = lastTestTime,
    lastSuccessfulLoginAt = lastSuccessfulLoginAt,
)

private fun ConnectionEntity.toDomain(cipher: CredentialCipher): ConnectionConfig = ConnectionConfig(
    id = id,
    name = name,
    protocol = ProtocolType.valueOf(protocol),
    host = host,
    port = port,
    username = username,
    password = encryptedPassword?.let(cipher::decrypt),
    privateKeyPem = encryptedPrivateKey?.let(cipher::decrypt),
    passphrase = encryptedPassphrase?.let(cipher::decrypt),
    remoteRootPath = remoteRootPath,
    timeoutSeconds = timeoutSeconds,
    retryCount = retryCount,
    keepAlive = keepAlive,
    hostKeyVerification = when (hostKeyVerificationMode) {
        "ACCEPT_ANY" -> HostKeyVerification.AcceptAny
        "PINNED" -> HostKeyVerification.PinnedFingerprint(pinnedFingerprint.orEmpty())
        else -> HostKeyVerification.StrictKnownHosts
    },
    ftpPassiveMode = ftpPassiveMode,
    ftpEncoding = ftpEncoding,
    useExplicitTls = useExplicitTls,
)

@Singleton
class ConnectionRepository @Inject constructor(
    private val dao: ConnectionDao,
    private val cipher: CredentialCipher,
) {
    fun observeAll(): Flow<List<ConnectionSummary>> = dao.observeAll().map { list -> list.map { it.toSummary() } }

    suspend fun getConfig(id: Long): ConnectionConfig? = dao.getById(id)?.toDomain(cipher)

    /** Inserts if [config].id == 0, otherwise updates in place. Secrets left null in [config] keep their stored value. */
    suspend fun save(config: ConnectionConfig): Long {
        val now = System.currentTimeMillis()
        val existing = if (config.id != 0L) dao.getById(config.id) else null
        val hostKeyMode = when (config.hostKeyVerification) {
            is HostKeyVerification.AcceptAny -> "ACCEPT_ANY"
            is HostKeyVerification.PinnedFingerprint -> "PINNED"
            HostKeyVerification.StrictKnownHosts -> "STRICT"
        }
        val entity = ConnectionEntity(
            id = config.id,
            name = config.name,
            protocol = config.protocol.name,
            host = config.host,
            port = config.port,
            username = config.username,
            encryptedPassword = config.password?.let(cipher::encrypt) ?: existing?.encryptedPassword,
            encryptedPrivateKey = config.privateKeyPem?.let(cipher::encrypt) ?: existing?.encryptedPrivateKey,
            encryptedPassphrase = config.passphrase?.let(cipher::encrypt) ?: existing?.encryptedPassphrase,
            remoteRootPath = config.remoteRootPath,
            timeoutSeconds = config.timeoutSeconds,
            retryCount = config.retryCount,
            keepAlive = config.keepAlive,
            hostKeyVerificationMode = hostKeyMode,
            pinnedFingerprint = (config.hostKeyVerification as? HostKeyVerification.PinnedFingerprint)?.sha256Fingerprint
                ?: existing?.pinnedFingerprint,
            ftpPassiveMode = config.ftpPassiveMode,
            ftpEncoding = config.ftpEncoding,
            useExplicitTls = config.useExplicitTls,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastTestStatus = existing?.lastTestStatus,
            lastTestTime = existing?.lastTestTime,
            lastSuccessfulLoginAt = existing?.lastSuccessfulLoginAt,
        )
        return if (existing != null) {
            dao.update(entity)
            entity.id
        } else {
            dao.insert(entity)
        }
    }

    suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    suspend fun duplicate(id: Long): Long {
        val original = dao.getById(id) ?: return -1
        val now = System.currentTimeMillis()
        return dao.insert(original.copy(id = 0, name = "${original.name} (copy)", createdAt = now, updatedAt = now))
    }

    suspend fun recordTestResult(id: Long, success: Boolean) {
        val now = System.currentTimeMillis()
        dao.updateLastTestResult(id, if (success) "SUCCESS" else "FAILED", now)
        if (success) dao.updateLastSuccessfulLogin(id, now)
    }
}
