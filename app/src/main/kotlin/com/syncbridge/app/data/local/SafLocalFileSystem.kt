package com.syncbridge.app.data.local

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.syncbridge.core.common.client.LocalFileSystem
import com.syncbridge.core.common.error.AppError
import com.syncbridge.core.common.model.FileFilterConfig
import com.syncbridge.core.common.model.FileFilterMatcher
import com.syncbridge.core.common.model.FileTreeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * [LocalFileSystem] backed by Android's Storage Access Framework. There is no guaranteed raw
 * filesystem path for a user-picked tree [Uri] (it may live on removable storage, be a
 * provider-backed pseudo-path, etc.), so every operation goes through [DocumentFile] /
 * [android.content.ContentResolver] rather than `java.io.File`.
 *
 * Known SAF limitations, documented rather than hidden:
 * - [DocumentFile.findFile] re-lists the parent directory's *entire* child set on every call, so deep
 *   trees with many siblings are O(n) per path segment. Acceptable for the MVP; a production follow-up
 *   should cache a directory's children for the duration of one scan.
 * - There is no atomic rename-if-exists; the temp-file dance in [openOutputStream] deletes any existing
 *   final-name file immediately before renaming the temp file into place, leaving a brief window where
 *   neither exists. This mirrors the equivalent trade-off in the SFTP/FTP clients.
 */
class SafLocalFileSystem(
    private val context: Context,
    private val treeUri: Uri,
) : LocalFileSystem {

    override suspend fun hasValidPermission(): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
    }

    private fun rootDocument(): DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri) ?: throw AppError.LocalPermissionRevoked(treeUri.toString())

    override suspend fun scan(filter: FileFilterConfig): FileTreeSnapshot = withContext(Dispatchers.IO) {
        val entries = mutableMapOf<String, FileTreeSnapshot.FileEntry>()
        scanDir(rootDocument(), "", filter, entries)
        FileTreeSnapshot(entries)
    }

    private fun scanDir(
        dir: DocumentFile,
        relativeDirPath: String,
        filter: FileFilterConfig,
        out: MutableMap<String, FileTreeSnapshot.FileEntry>,
    ) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            val relativePath = if (relativeDirPath.isEmpty()) name else "$relativeDirPath/$name"
            if (child.isDirectory) {
                val segments = relativePath.split('/')
                if (segments.any { it in filter.excludeFolderNames }) continue
                scanDir(child, relativePath, filter, out)
            } else {
                if (!FileFilterMatcher.shouldInclude(filter, relativePath, child.length())) continue
                out[relativePath] = FileTreeSnapshot.FileEntry(relativePath, child.length(), child.lastModified())
            }
        }
    }

    override suspend fun openInputStream(relativePath: String): InputStream = withContext(Dispatchers.IO) {
        val doc = resolveExisting(relativePath) ?: throw AppError.Unknown("Local file not found: $relativePath")
        context.contentResolver.openInputStream(doc.uri) ?: throw AppError.Unknown("Could not open $relativePath")
    }

    override suspend fun openOutputStream(relativePath: String, sizeHintBytes: Long): OutputStream = withContext(Dispatchers.IO) {
        val (dirSegments, fileName) = splitPath(relativePath)
        val parentDir = resolveOrCreateDir(dirSegments)
        val tempName = "$fileName.downloading"
        parentDir.findFile(tempName)?.delete()
        val tempDoc = parentDir.createFile(guessMimeType(fileName), tempName)
            ?: throw AppError.Unknown("Could not create temp file for $relativePath")
        val rawOut = context.contentResolver.openOutputStream(tempDoc.uri)
            ?: throw AppError.Unknown("Could not open output stream for $relativePath")
        RenameOnCloseOutputStream(rawOut) {
            parentDir.findFile(fileName)?.delete()
            if (!tempDoc.renameTo(fileName)) {
                throw AppError.PartialTransfer(relativePath)
            }
        }
    }

    override suspend fun delete(relativePath: String, moveToRecycleBin: Boolean) = withContext(Dispatchers.IO) {
        val doc = resolveExisting(relativePath) ?: return@withContext
        if (moveToRecycleBin) {
            // SAF has no cross-tree "move"; renaming in place under a recycle marker is the cheapest
            // way to keep the bytes recoverable without a full stream copy. The `recycle_bin` Room
            // table (see :core:database) is where the app-level restore flow tracks these for the UI;
            // wiring that decorator is on the post-MVP roadmap (see README).
            doc.renameTo(".syncbridge-recycle-bin_${System.currentTimeMillis()}_${doc.name}")
        } else {
            doc.delete()
        }
    }

    override suspend fun createDirectories(relativeDirPath: String) = withContext(Dispatchers.IO) {
        resolveOrCreateDir(relativeDirPath.split('/').filter { it.isNotEmpty() })
        Unit
    }

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        resolveExisting(relativePath) != null
    }

    override suspend fun sizeOf(relativePath: String): Long? = withContext(Dispatchers.IO) {
        resolveExisting(relativePath)?.length()
    }

    override suspend fun freeSpaceBytes(): Long = withContext(Dispatchers.IO) {
        // SAF doesn't expose free space for an arbitrary tree (e.g. an SD card or another provider),
        // so this is a best-effort signal from internal storage for the storage-full pre-check.
        StatFs(context.filesDir.path).let { it.availableBlocksLong * it.blockSizeLong }
    }

    private fun splitPath(relativePath: String): Pair<List<String>, String> {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        return segments.dropLast(1) to segments.last()
    }

    private fun resolveExisting(relativePath: String): DocumentFile? {
        var current = rootDocument()
        for (segment in relativePath.split('/').filter { it.isNotEmpty() }) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun resolveOrCreateDir(segments: List<String>): DocumentFile {
        var current = rootDocument()
        for (segment in segments) {
            current = current.findFile(segment)
                ?: current.createDirectory(segment)
                ?: throw AppError.Unknown("Could not create folder $segment")
        }
        return current
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private class RenameOnCloseOutputStream(
        private val delegate: OutputStream,
        private val onClose: () -> Unit,
    ) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()
        override fun close() {
            delegate.close()
            onClose()
        }
    }
}
