package com.syncbridge.core.common.client

import com.syncbridge.core.common.model.RemoteFileMetadata

/** Reports byte-level progress for a single file transfer. Invoked on a background thread. */
fun interface TransferProgressListener {
    fun onProgress(bytesTransferred: Long, totalBytes: Long)
}

/**
 * Protocol-agnostic abstraction over a remote file store. Implementations: SftpRemoteFileClient
 * (protocol:sftp), FtpRemoteFileClient (protocol:ftp). Adding a new protocol (WebDAV, S3, ...) means
 * writing one more implementation of this interface; nothing above it needs to change.
 *
 * All paths are absolute remote paths using '/' separators, rooted at the connection's remote root.
 * Implementations must reject paths that resolve outside that root (see PathValidator).
 *
 * Not thread-safe: callers must serialize access to a single client instance, or open one instance
 * per concurrent operation (subject to the profile's max-parallel-transfers setting).
 */
interface RemoteFileClient : AutoCloseable {

    /** Opens the underlying connection. Must be called before any other operation. */
    suspend fun connect()

    /** Idempotent; safe to call even if never connected or already disconnected. */
    suspend fun disconnect()

    /** Lightweight round-trip (e.g. stat the remote root) used by the "Test connection" UI action. */
    suspend fun testConnection(): ConnectionTestResult

    suspend fun listFiles(path: String): List<RemoteFileMetadata>

    suspend fun createFolder(path: String)

    /**
     * Uploads the bytes returned by [localBytes] to [remotePath]. Implementations must stream (never
     * buffer the whole file in memory) and must upload to a temporary sibling name first, verify the
     * size, then rename into place, so a killed process never leaves a corrupt file at [remotePath].
     */
    suspend fun uploadFile(
        localBytes: suspend () -> java.io.InputStream,
        sizeBytes: Long,
        remotePath: String,
        progressListener: TransferProgressListener? = null,
    )

    /**
     * Downloads [remotePath] into whatever [localSink] returns. [localSink] is expected to itself
     * write to a temporary sibling and rename into place on success (see
     * `LocalFileSystem.openOutputStream`), since local storage goes through SAF rather than a plain
     * filesystem path and only the SAF-side implementation knows how to do that safely.
     */
    suspend fun downloadFile(
        remotePath: String,
        localSink: suspend () -> java.io.OutputStream,
        progressListener: TransferProgressListener? = null,
    )

    suspend fun deleteFile(path: String)

    suspend fun renameFile(oldPath: String, newPath: String)

    suspend fun fileExists(path: String): Boolean

    suspend fun getFileMetadata(path: String): RemoteFileMetadata?

    override fun close() {
        // Coroutine-based disconnect cannot run from a synchronous close(); implementations override
        // this only for best-effort cleanup of non-suspend resources (sockets, thread pools).
    }
}

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val serverIdentification: String? = null,
)
