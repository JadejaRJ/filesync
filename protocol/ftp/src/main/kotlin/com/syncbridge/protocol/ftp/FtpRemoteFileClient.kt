package com.syncbridge.protocol.ftp

import com.syncbridge.core.common.client.ConnectionTestResult
import com.syncbridge.core.common.client.RemoteFileClient
import com.syncbridge.core.common.client.TransferProgressListener
import com.syncbridge.core.common.error.AppError
import com.syncbridge.core.common.model.ConnectionConfig
import com.syncbridge.core.common.model.RemoteFileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * [RemoteFileClient] implementation backed by Apache Commons Net. Plain FTP transmits credentials and
 * data unencrypted; [ConnectionConfig.isInsecure] should have already surfaced a warning to the user
 * before this client is ever constructed (see `AddConnectionScreen`) — this class does not re-check it.
 *
 * FTP has no atomic rename-with-overwrite the way SFTP's posix-rename extension does, so the
 * upload-to-temp-name-then-rename here has the same brief "destination momentarily missing" window as
 * the SFTP fallback path: the existing destination file (if any) is removed just before the rename.
 */
class FtpRemoteFileClient(
    private val config: ConnectionConfig,
) : RemoteFileClient {

    private var client: FTPClient? = null

    private fun requireClient(): FTPClient = client ?: error("connect() must be called before using FtpRemoteFileClient")

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val ftp = FTPClient()
        ftp.connectTimeout = config.timeoutSeconds * 1000
        ftp.controlEncoding = config.ftpEncoding

        try {
            ftp.connect(config.host, config.port)
        } catch (e: UnknownHostException) {
            throw AppError.DnsFailure(config.host, e)
        } catch (e: ConnectException) {
            throw AppError.HostUnreachable(config.host, e)
        } catch (e: SocketTimeoutException) {
            throw AppError.ConnectionTimeout(e)
        } catch (e: java.io.IOException) {
            runCatching { ftp.disconnect() }
            throw AppError.HostUnreachable(config.host, e)
        }

        if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
            val code = ftp.replyCode
            runCatching { ftp.disconnect() }
            throw AppError.HostUnreachable(config.host, Exception("FTP server refused connection, reply=$code"))
        }

        ftp.soTimeout = config.timeoutSeconds * 1000

        if (!ftp.login(config.username, config.password.orEmpty())) {
            val message = ftp.replyString
            runCatching { ftp.disconnect() }
            throw AppError.AuthFailed(message)
        }

        ftp.setFileType(FTP.BINARY_FILE_TYPE)
        if (config.ftpPassiveMode) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
        if (config.keepAlive) ftp.setControlKeepAliveTimeout(java.time.Duration.ofSeconds(30))

        client = ftp
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { client?.logout() }
        runCatching { client?.disconnect() }
        client = null
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        try {
            connect()
            val ftp = requireClient()
            val systemType = runCatching { ftp.systemType }.getOrNull()
            ftp.listFiles(config.remoteRootPath) // throws/returns null-safe empty on bad path; validates access
            ConnectionTestResult(success = true, message = "Connected successfully", serverIdentification = systemType)
        } catch (e: AppError) {
            ConnectionTestResult(success = false, message = e.userMessage)
        } catch (e: Exception) {
            ConnectionTestResult(success = false, message = e.message ?: "Could not connect to the server.")
        } finally {
            disconnect()
        }
    }

    override suspend fun listFiles(path: String): List<RemoteFileMetadata> = withContext(Dispatchers.IO) {
        withMappedErrors(path) {
            val ftp = requireClient()
            val files: Array<FTPFile> = ftp.listFiles(path) ?: emptyArray()
            if (!FTPReply.isPositiveCompletion(ftp.replyCode) && files.isEmpty()) {
                if (ftp.replyCode == FTPReply.FILE_UNAVAILABLE) throw AppError.RemoteFolderNotFound(path)
            }
            files.filter { it.name != "." && it.name != ".." }.map { file -> file.toMetadata(path) }
        }
    }

    override suspend fun createFolder(path: String) = withContext(Dispatchers.IO) {
        withMappedErrors(path) {
            val ftp = requireClient()
            val segments = path.split('/').filter { it.isNotEmpty() }
            var current = ""
            for (segment in segments) {
                current = "$current/$segment"
                if (!ftp.changeWorkingDirectory(current)) {
                    ftp.makeDirectory(current)
                }
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
            val ftp = requireClient()
            val tempPath = "$remotePath.uploading"
            val input = ProgressInputStream(localBytes(), sizeBytes, progressListener)
            val stored = input.use { ftp.storeFile(tempPath, it) }
            if (!stored || !FTPReply.isPositiveCompletion(ftp.replyCode)) {
                runCatching { ftp.deleteFile(tempPath) }
                throw AppError.PartialTransfer(remotePath)
            }
            val uploadedSize = runCatching { ftp.getSize(tempPath)?.trim()?.toLongOrNull() }.getOrNull()
            if (uploadedSize != null && uploadedSize != sizeBytes) {
                runCatching { ftp.deleteFile(tempPath) }
                throw AppError.PartialTransfer(remotePath)
            }
            renameIntoPlace(ftp, tempPath, remotePath)
        }
    }

    override suspend fun downloadFile(
        remotePath: String,
        localSink: suspend () -> OutputStream,
        progressListener: TransferProgressListener?,
    ) = withContext(Dispatchers.IO) {
        withMappedErrors(remotePath) {
            val ftp = requireClient()
            val totalSize = runCatching { ftp.getSize(remotePath)?.trim()?.toLongOrNull() }.getOrNull() ?: 0
            val output = ProgressOutputStream(localSink(), totalSize, progressListener)
            val retrieved = output.use { ftp.retrieveFile(remotePath, it) }
            if (!retrieved || !FTPReply.isPositiveCompletion(ftp.replyCode)) {
                throw AppError.PartialTransfer(remotePath)
            }
        }
    }

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        withMappedErrors(path) {
            val ftp = requireClient()
            if (!ftp.deleteFile(path)) {
                if (!ftp.removeDirectory(path)) {
                    throw mapReplyCode(path, ftp.replyCode, ftp.replyString)
                }
            }
        }
    }

    override suspend fun renameFile(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        withMappedErrors(oldPath) { renameIntoPlace(requireClient(), oldPath, newPath) }
    }

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        getFileMetadata(path) != null
    }

    override suspend fun getFileMetadata(path: String): RemoteFileMetadata? = withContext(Dispatchers.IO) {
        withMappedErrors(path) {
            val parent = path.substringBeforeLast('/', missingDelimiterValue = "/")
            val name = path.substringAfterLast('/')
            val files = requireClient().listFiles(parent.ifEmpty { "/" }) ?: emptyArray()
            files.firstOrNull { it.name == name }?.toMetadata(parent)
        }
    }

    override fun close() {
        runCatching { client?.disconnect() }
    }

    // ---- internals ----

    private fun FTPFile.toMetadata(parentPath: String): RemoteFileMetadata {
        val normalizedParent = parentPath.trimEnd('/')
        val fullPath = if (normalizedParent.isEmpty()) "/$name" else "$normalizedParent/$name"
        return RemoteFileMetadata(
            path = fullPath,
            name = name,
            isDirectory = isDirectory,
            size = size,
            lastModifiedEpochMillis = timestamp?.timeInMillis ?: 0,
            isSymlink = isSymbolicLink,
        )
    }

    private fun renameIntoPlace(ftp: FTPClient, fromPath: String, toPath: String) {
        runCatching { ftp.deleteFile(toPath) }
        if (!ftp.rename(fromPath, toPath)) {
            throw mapReplyCode(toPath, ftp.replyCode, ftp.replyString)
        }
    }

    private inline fun <T> withMappedErrors(path: String, block: () -> T): T {
        try {
            return block()
        } catch (e: AppError) {
            throw e
        } catch (e: java.io.IOException) {
            throw AppError.ServerClosedConnection(e)
        }
    }

    private fun mapReplyCode(path: String, code: Int, message: String): AppError = when (code) {
        FTPReply.FILE_UNAVAILABLE, 450 -> AppError.RemoteFolderNotFound(path)
        FTPReply.NOT_LOGGED_IN -> AppError.PermissionDenied(path)
        FTPReply.INSUFFICIENT_STORAGE -> AppError.ServerQuotaExceeded()
        FTPReply.CANNOT_OPEN_DATA_CONNECTION -> AppError.FtpPassiveModeFailed()
        else -> AppError.Unknown("FTP reply $code: $message")
    }

    /** Wraps the source stream so read() calls report cumulative progress without buffering. */
    private class ProgressInputStream(
        input: InputStream,
        private val totalBytes: Long,
        private val listener: TransferProgressListener?,
    ) : FilterInputStream(input) {
        private var transferred = 0L

        override fun read(): Int {
            val b = super.read()
            if (b != -1) report(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = super.read(b, off, len)
            if (read > 0) report(read.toLong())
            return read
        }

        private fun report(count: Long) {
            transferred += count
            listener?.onProgress(transferred, totalBytes)
        }
    }

    private class ProgressOutputStream(
        output: OutputStream,
        private val totalBytes: Long,
        private val listener: TransferProgressListener?,
    ) : FilterOutputStream(output) {
        private var transferred = 0L

        override fun write(b: Int) {
            out.write(b)
            report(1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            report(len.toLong())
        }

        private fun report(count: Long) {
            transferred += count
            listener?.onProgress(transferred, totalBytes)
        }
    }
}
