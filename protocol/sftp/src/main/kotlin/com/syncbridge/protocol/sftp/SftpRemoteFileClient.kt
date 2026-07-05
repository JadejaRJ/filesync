package com.syncbridge.protocol.sftp

import com.syncbridge.core.common.client.ConnectionTestResult
import com.syncbridge.core.common.client.RemoteFileClient
import com.syncbridge.core.common.client.TransferProgressListener
import com.syncbridge.core.common.error.AppError
import com.syncbridge.core.common.model.ConnectionConfig
import com.syncbridge.core.common.model.HostKeyVerification
import com.syncbridge.core.common.model.RemoteFileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RenameFlags
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.PublicKey
import java.util.EnumSet

/**
 * [RemoteFileClient] implementation backed by SSHJ. One instance wraps one SSH connection + SFTP
 * session; callers should not share an instance across concurrent operations (see interface KDoc).
 *
 * Host key handling: for [HostKeyVerification.StrictKnownHosts], the fingerprint SyncBridge has
 * previously accepted for this host is read from [knownHostsStore] *before* connecting (host-key
 * verification happens on a synchronous SSHJ callback thread, so it cannot itself suspend to query
 * Room). If no fingerprint is on file, or it doesn't match what the server presents, [connect] throws
 * [AppError.HostKeyUnknown] / [AppError.HostKeyMismatch] and does not proceed — the caller is expected
 * to show the fingerprint to the user and, on explicit accept, call
 * `knownHostsStore.storeFingerprint(...)` before retrying.
 */
class SftpRemoteFileClient(
    private val config: ConnectionConfig,
    private val knownHostsStore: SftpKnownHostsStore,
) : RemoteFileClient {

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    private fun requireSftp(): SFTPClient = sftpClient ?: error("connect() must be called before using SftpRemoteFileClient")

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val storedFingerprint = if (config.hostKeyVerification is HostKeyVerification.StrictKnownHosts) {
            knownHostsStore.getStoredFingerprint(config.host, config.port)
        } else {
            null
        }

        val client = SSHClient()
        client.connectTimeout = config.timeoutSeconds * 1000
        client.timeout = config.timeoutSeconds * 1000
        client.addHostKeyVerifier(buildHostKeyVerifier(storedFingerprint))

        try {
            client.connect(config.host, config.port)
        } catch (e: UnknownHostException) {
            throw AppError.DnsFailure(config.host, e)
        } catch (e: ConnectException) {
            throw AppError.HostUnreachable(config.host, e)
        } catch (e: SocketTimeoutException) {
            throw AppError.ConnectionTimeout(e)
        } catch (e: TransportException) {
            throw resolveHostKeyError(e, storedFingerprint)
        }

        if (config.keepAlive) {
            client.connection.keepAlive.keepAliveInterval = 30
        }

        try {
            authenticate(client)
        } catch (e: UserAuthException) {
            throw AppError.AuthFailed(e.message, e)
        }

        sshClient = client
        sftpClient = client.newSFTPClient()
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { sftpClient?.close() }
        runCatching { sshClient?.disconnect() }
        sftpClient = null
        sshClient = null
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        try {
            connect()
            val sftp = requireSftp()
            sftp.canonicalize(config.remoteRootPath)
            ConnectionTestResult(success = true, message = "Connected successfully", serverIdentification = sshClient?.transport?.serverVersion)
        } catch (e: AppError) {
            ConnectionTestResult(success = false, message = e.userMessage)
        } finally {
            disconnect()
        }
    }

    override suspend fun listFiles(path: String): List<RemoteFileMetadata> = withContext(Dispatchers.IO) {
        withMappedErrors(path) {
            requireSftp().ls(path).map { info ->
                RemoteFileMetadata(
                    path = info.path,
                    name = info.name,
                    isDirectory = info.isDirectory,
                    size = info.attributes.size,
                    lastModifiedEpochMillis = info.attributes.mtime * 1000,
                    isSymlink = info.attributes.type == FileMode.Type.SYMLINK,
                )
            }
        }
    }

    override suspend fun createFolder(path: String) = withContext(Dispatchers.IO) {
        withMappedErrors(path) {
            if (!fileExistsInternal(path)) {
                requireSftp().mkdirs(path)
            }
        }
    }

    override suspend fun uploadFile(
        localBytes: suspend () -> InputStream,
        sizeBytes: Long,
        remotePath: String,
        progressListener: TransferProgressListener?,
    ) = withContext(Dispatchers.IO) {
        withMappedErrors(remotePath) {
            val sftp = requireSftp()
            val tempPath = "$remotePath.uploading"
            val remoteFile = sftp.open(tempPath, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
            var transferred = 0L
            try {
                remoteFile.RemoteFileOutputStream().use { out ->
                    localBytes().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            transferred += read
                            progressListener?.onProgress(transferred, sizeBytes)
                        }
                    }
                }
            } finally {
                remoteFile.close()
            }

            val uploadedSize = sftp.size(tempPath)
            if (uploadedSize != transferred) {
                runCatching { sftp.rm(tempPath) }
                throw AppError.PartialTransfer(remotePath)
            }
            renameIntoPlace(sftp, tempPath, remotePath)
        }
    }

    override suspend fun downloadFile(
        remotePath: String,
        localSink: suspend () -> OutputStream,
        progressListener: TransferProgressListener?,
    ) = withContext(Dispatchers.IO) {
        withMappedErrors(remotePath) {
            val sftp = requireSftp()
            val remoteFile = sftp.open(remotePath, EnumSet.of(OpenMode.READ))
            val totalSize = remoteFile.length()
            try {
                localSink().use { out ->
                    remoteFile.RemoteFileInputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var transferred = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            transferred += read
                            progressListener?.onProgress(transferred, totalSize)
                        }
                    }
                }
            } finally {
                remoteFile.close()
            }
        }
    }

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        withMappedErrors(path) {
            val sftp = requireSftp()
            if (sftp.stat(path).type == FileMode.Type.DIRECTORY) sftp.rmdir(path) else sftp.rm(path)
        }
    }

    override suspend fun renameFile(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        withMappedErrors(oldPath) { renameIntoPlace(requireSftp(), oldPath, newPath) }
    }

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        fileExistsInternal(path)
    }

    override suspend fun getFileMetadata(path: String): RemoteFileMetadata? = withContext(Dispatchers.IO) {
        withMappedErrors(path) {
            val attrs = try {
                requireSftp().stat(path)
            } catch (e: net.schmizz.sshj.sftp.SFTPException) {
                if (e.statusCode == net.schmizz.sshj.sftp.Response.StatusCode.NO_SUCH_FILE) return@withMappedErrors null
                throw e
            }
            RemoteFileMetadata(
                path = path,
                name = path.substringAfterLast('/'),
                isDirectory = attrs.type == FileMode.Type.DIRECTORY,
                size = attrs.size,
                lastModifiedEpochMillis = attrs.mtime * 1000,
                isSymlink = attrs.type == FileMode.Type.SYMLINK,
            )
        }
    }

    override fun close() {
        runCatching { sftpClient?.close() }
        runCatching { sshClient?.disconnect() }
    }

    // ---- internals ----

    private fun fileExistsInternal(path: String): Boolean = try {
        requireSftp().statExistence(path) != null
    } catch (e: Exception) {
        false
    }

    private fun renameIntoPlace(sftp: SFTPClient, fromPath: String, toPath: String) {
        try {
            sftp.rename(fromPath, toPath, EnumSet.of(RenameFlags.OVERWRITE))
        } catch (e: Exception) {
            // Server doesn't support the posix-rename/OVERWRITE extension: fall back to remove-then-rename.
            // This reopens a brief window where `toPath` doesn't exist; acceptable trade-off documented in README.
            runCatching { sftp.rm(toPath) }
            sftp.rename(fromPath, toPath)
        }
    }

    private suspend fun authenticate(client: SSHClient) {
        val privateKeyPem = config.privateKeyPem
        if (privateKeyPem != null) {
            val keyFile = OpenSSHKeyFile()
            val passphrase = config.passphrase
            if (passphrase != null) {
                keyFile.init(StringReader(privateKeyPem), PasswordUtils.createOneOff(passphrase.toCharArray()))
            } else {
                keyFile.init(StringReader(privateKeyPem))
            }
            client.authPublickey(config.username, keyFile)
        } else {
            client.authPassword(config.username, config.password.orEmpty())
        }
    }

    private fun buildHostKeyVerifier(storedFingerprint: String?): HostKeyVerifier {
        return when (val verification = config.hostKeyVerification) {
            is HostKeyVerification.AcceptAny -> PromiscuousVerifier()
            is HostKeyVerification.PinnedFingerprint -> object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    val fingerprint = SecurityUtils.getFingerprint(key)
                    lastHostKeyIssue = if (fingerprint == verification.sha256Fingerprint) {
                        null
                    } else {
                        HostKeyIssue.Mismatch(verification.sha256Fingerprint, fingerprint)
                    }
                    return lastHostKeyIssue == null
                }

                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
            }
            HostKeyVerification.StrictKnownHosts -> object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    val fingerprint = SecurityUtils.getFingerprint(key)
                    lastHostKeyIssue = when {
                        storedFingerprint == null -> HostKeyIssue.Unknown(fingerprint)
                        storedFingerprint != fingerprint -> HostKeyIssue.Mismatch(storedFingerprint, fingerprint)
                        else -> null
                    }
                    return lastHostKeyIssue == null
                }

                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
            }
        }
    }

    /** Set synchronously by the [HostKeyVerifier] callback during [SSHClient.connect]; read right after. */
    @Volatile
    private var lastHostKeyIssue: HostKeyIssue? = null

    private fun resolveHostKeyError(e: TransportException, storedFingerprint: String?): AppError {
        return when (val issue = lastHostKeyIssue) {
            is HostKeyIssue.Unknown -> AppError.HostKeyUnknown(config.host, issue.fingerprint)
            is HostKeyIssue.Mismatch -> AppError.HostKeyMismatch(config.host, issue.expected, issue.actual)
            null -> AppError.Unknown(e.message, e)
        }
    }

    private sealed class HostKeyIssue {
        data class Unknown(val fingerprint: String) : HostKeyIssue()
        data class Mismatch(val expected: String, val actual: String) : HostKeyIssue()
    }

    private inline fun <T> withMappedErrors(path: String, block: () -> T): T {
        try {
            return block()
        } catch (e: AppError) {
            throw e
        } catch (e: net.schmizz.sshj.sftp.SFTPException) {
            throw mapSftpException(path, e)
        } catch (e: java.io.IOException) {
            throw AppError.ServerClosedConnection(e)
        }
    }

    private fun mapSftpException(path: String, e: net.schmizz.sshj.sftp.SFTPException): AppError {
        return when (e.statusCode) {
            net.schmizz.sshj.sftp.Response.StatusCode.NO_SUCH_FILE -> AppError.RemoteFolderNotFound(path)
            net.schmizz.sshj.sftp.Response.StatusCode.PERMISSION_DENIED -> AppError.PermissionDenied(path, e)
            net.schmizz.sshj.sftp.Response.StatusCode.NO_SPACE_ON_FILESYSTEM,
            net.schmizz.sshj.sftp.Response.StatusCode.QUOTA_EXCEEDED,
            -> AppError.ServerQuotaExceeded()
            else -> AppError.Unknown(e.message, e)
        }
    }
}
