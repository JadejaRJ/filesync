package com.syncbridge.core.sync.engine.fakes

import com.syncbridge.core.common.client.ConnectionTestResult
import com.syncbridge.core.common.client.RemoteFileClient
import com.syncbridge.core.common.client.TransferProgressListener
import com.syncbridge.core.common.model.RemoteFileMetadata

/** In-memory [RemoteFileClient] rooted at "/". Supports scripted transient failures for retry tests. */
class FakeRemoteFileClient(
    initialFiles: Map<String, ByteArray> = emptyMap(),
) : RemoteFileClient {
    val files = initialFiles.toMutableMap()
    val modifiedTimes = initialFiles.keys.associateWith { 1_000L }.toMutableMap()
    var failUploadsUntilAttempt: Int = 0
    private var uploadAttempts = 0
    var connected = false

    override suspend fun connect() {
        connected = true
    }

    override suspend fun disconnect() {
        connected = false
    }

    override suspend fun testConnection() = ConnectionTestResult(true, "ok")

    override suspend fun listFiles(path: String): List<RemoteFileMetadata> {
        val prefix = if (path == "/") "" else path.removePrefix("/").trimEnd('/') + "/"
        return files.keys
            .filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains('/') }
            .map { key ->
                RemoteFileMetadata(
                    path = "/$key",
                    name = key.removePrefix(prefix),
                    isDirectory = false,
                    size = files.getValue(key).size.toLong(),
                    lastModifiedEpochMillis = modifiedTimes[key] ?: 0,
                )
            }
    }

    override suspend fun createFolder(path: String) = Unit

    override suspend fun uploadFile(
        localBytes: suspend () -> java.io.InputStream,
        sizeBytes: Long,
        remotePath: String,
        progressListener: TransferProgressListener?,
    ) {
        uploadAttempts++
        if (uploadAttempts <= failUploadsUntilAttempt) {
            throw java.io.IOException("simulated transient network failure")
        }
        val key = remotePath.removePrefix("/")
        val bytes = localBytes().use { it.readBytes() }
        files[key] = bytes
        modifiedTimes[key] = 2_000L
        progressListener?.onProgress(bytes.size.toLong(), sizeBytes)
    }

    override suspend fun downloadFile(
        remotePath: String,
        localSink: suspend () -> java.io.OutputStream,
        progressListener: TransferProgressListener?,
    ) {
        val key = remotePath.removePrefix("/")
        val bytes = files[key] ?: error("missing remote file $remotePath")
        localSink().use { it.write(bytes) }
        progressListener?.onProgress(bytes.size.toLong(), bytes.size.toLong())
    }

    override suspend fun deleteFile(path: String) {
        files.remove(path.removePrefix("/"))
    }

    override suspend fun renameFile(oldPath: String, newPath: String) {
        val bytes = files.remove(oldPath.removePrefix("/")) ?: return
        files[newPath.removePrefix("/")] = bytes
    }

    override suspend fun fileExists(path: String) = files.containsKey(path.removePrefix("/"))

    override suspend fun getFileMetadata(path: String): RemoteFileMetadata? {
        val key = path.removePrefix("/")
        val bytes = files[key] ?: return null
        return RemoteFileMetadata(path, key.substringAfterLast('/'), false, bytes.size.toLong(), modifiedTimes[key] ?: 0)
    }
}
