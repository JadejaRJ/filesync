package com.syncbridge.core.common.error

/**
 * Every failure surfaced by the sync engine, protocol clients, or storage layer funnels through this
 * hierarchy so the UI can render a human-readable message + suggested fix instead of a raw exception.
 * Never construct these from raw credential-bearing exception messages; strip secrets in [technicalDetail].
 */
sealed class AppError(
    val code: String,
    val userMessage: String,
    val suggestedFix: String,
    val retryable: Boolean,
    val technicalDetail: String? = null,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    class AuthFailed(detail: String? = null, cause: Throwable? = null) : AppError(
        code = "SFTP_AUTH_FAILED",
        userMessage = "Login failed. Check the username, password, or private key.",
        suggestedFix = "Retry, or edit the connection to fix credentials.",
        retryable = true,
        technicalDetail = detail,
        cause = cause,
    )

    class HostUnreachable(host: String, cause: Throwable? = null) : AppError(
        code = "HOST_UNREACHABLE",
        userMessage = "Could not reach $host. The server may be offline or the address may be wrong.",
        suggestedFix = "Check the host/IP address and your network connection, then retry.",
        retryable = true,
        cause = cause,
    )

    class DnsFailure(host: String, cause: Throwable? = null) : AppError(
        code = "DNS_FAILURE",
        userMessage = "Could not resolve the address \"$host\".",
        suggestedFix = "Check for typos in the host name, or use the server's IP address instead.",
        retryable = true,
        cause = cause,
    )

    class ConnectionTimeout(cause: Throwable? = null) : AppError(
        code = "CONNECTION_TIMEOUT",
        userMessage = "The server took too long to respond.",
        suggestedFix = "Check your network connection or increase the timeout in connection settings.",
        retryable = true,
        cause = cause,
    )

    class PermissionDenied(path: String, cause: Throwable? = null) : AppError(
        code = "PERMISSION_DENIED",
        userMessage = "You don't have permission to access \"$path\" on the server.",
        suggestedFix = "Check folder permissions on the server or choose a different remote folder.",
        retryable = false,
        cause = cause,
    )

    class RemoteFolderNotFound(path: String) : AppError(
        code = "REMOTE_FOLDER_NOT_FOUND",
        userMessage = "The remote folder \"$path\" no longer exists.",
        suggestedFix = "Re-select the remote folder for this sync profile.",
        retryable = false,
    )

    class LocalPermissionRevoked(uri: String) : AppError(
        code = "LOCAL_PERMISSION_REVOKED",
        userMessage = "Android has revoked access to the local folder for this profile.",
        suggestedFix = "Open the sync profile and re-select the local folder to grant access again.",
        retryable = false,
    )

    class StorageFull(path: String) : AppError(
        code = "STORAGE_FULL",
        userMessage = "There isn't enough free space to complete this transfer.",
        suggestedFix = "Free up space on the destination and retry.",
        retryable = true,
    )

    class NetworkDisconnected : AppError(
        code = "NETWORK_DISCONNECTED",
        userMessage = "Sync paused because the network connection was lost.",
        suggestedFix = "Reconnect to Wi-Fi or mobile data; sync will resume automatically.",
        retryable = true,
    )

    class FileLocked(path: String) : AppError(
        code = "FILE_LOCKED",
        userMessage = "\"$path\" is in use and could not be read or written.",
        suggestedFix = "Close any app using this file and retry.",
        retryable = true,
    )

    class PartialTransfer(path: String, cause: Throwable? = null) : AppError(
        code = "PARTIAL_TRANSFER",
        userMessage = "The transfer of \"$path\" was interrupted before it finished.",
        suggestedFix = "SyncBridge will retry automatically; you can also retry manually.",
        retryable = true,
        cause = cause,
    )

    class ServerClosedConnection(cause: Throwable? = null) : AppError(
        code = "SERVER_CLOSED_CONNECTION",
        userMessage = "The server closed the connection unexpectedly.",
        suggestedFix = "Retry. If this keeps happening, check the server's connection limits.",
        retryable = true,
        cause = cause,
    )

    class FtpPassiveModeFailed(cause: Throwable? = null) : AppError(
        code = "FTP_PASSIVE_MODE_FAILED",
        userMessage = "Could not establish a passive-mode data connection to the FTP server.",
        suggestedFix = "Try switching to active mode in the connection settings, or check server/firewall configuration.",
        retryable = true,
        cause = cause,
    )

    class HostKeyMismatch(host: String, expectedFingerprint: String, actualFingerprint: String) : AppError(
        code = "SFTP_HOST_KEY_MISMATCH",
        userMessage = "The identity of \"$host\" has changed since you last connected. This could indicate a security risk.",
        suggestedFix = "Verify the server's new host key out-of-band before accepting it. Do not proceed if unsure.",
        retryable = false,
        technicalDetail = "expected=$expectedFingerprint actual=$actualFingerprint",
    )

    class HostKeyUnknown(host: String, fingerprint: String) : AppError(
        code = "SFTP_HOST_KEY_UNKNOWN",
        userMessage = "SyncBridge has not seen a host key for \"$host\" before.",
        suggestedFix = "Verify the fingerprint matches your server, then accept it to continue.",
        retryable = false,
        technicalDetail = "fingerprint=$fingerprint",
    )

    class DuplicateFile(path: String) : AppError(
        code = "DUPLICATE_FILE",
        userMessage = "\"$path\" already exists at the destination with different content.",
        suggestedFix = "Resolve the conflict to choose which version to keep.",
        retryable = false,
    )

    class ConflictDetected(path: String) : AppError(
        code = "CONFLICT_DETECTED",
        userMessage = "\"$path\" was changed on both the device and the server since the last sync.",
        suggestedFix = "Open sync details to choose which version to keep, or change the profile's conflict rule.",
        retryable = false,
    )

    class BackgroundRestricted : AppError(
        code = "BACKGROUND_RESTRICTED",
        userMessage = "Android is restricting SyncBridge from running in the background.",
        suggestedFix = "Disable battery optimization for SyncBridge in system settings to allow scheduled sync.",
        retryable = false,
    )

    class BatteryOptimizationBlocking : AppError(
        code = "BATTERY_OPTIMIZATION_BLOCKING",
        userMessage = "Battery optimization may prevent scheduled sync from running reliably.",
        suggestedFix = "Exempt SyncBridge from battery optimization in Android settings.",
        retryable = false,
    )

    class TooManyFiles(count: Int, limit: Int) : AppError(
        code = "TOO_MANY_FILES",
        userMessage = "This folder has $count files, which exceeds the safety limit of $limit for a single sync run.",
        suggestedFix = "Narrow the sync scope with filters, or increase the limit in advanced settings.",
        retryable = false,
    )

    class FileTooLarge(path: String, sizeBytes: Long) : AppError(
        code = "FILE_TOO_LARGE",
        userMessage = "\"$path\" (${sizeBytes / (1024 * 1024)} MB) exceeds the size filter for this profile.",
        suggestedFix = "Increase the max file size filter if you want this file synced.",
        retryable = false,
    )

    class InvalidFilename(path: String) : AppError(
        code = "INVALID_FILENAME",
        userMessage = "\"$path\" contains characters that aren't valid on the destination filesystem.",
        suggestedFix = "Rename the file to remove unsupported characters, or skip it.",
        retryable = false,
    )

    class UnsupportedSymlink(path: String) : AppError(
        code = "UNSUPPORTED_SYMLINK",
        userMessage = "\"$path\" is a symbolic link, which SyncBridge does not follow for safety reasons.",
        suggestedFix = "Sync the link's target directly if needed.",
        retryable = false,
    )

    class ServerQuotaExceeded : AppError(
        code = "SERVER_QUOTA_EXCEEDED",
        userMessage = "The remote server rejected the upload because its storage quota is full.",
        suggestedFix = "Free up space on the server or contact your server administrator.",
        retryable = false,
    )

    class MassDeletionBlocked(count: Int, percent: Int) : AppError(
        code = "MASS_DELETION_BLOCKED",
        userMessage = "This sync would delete $count files ($percent% of the folder). It has been paused for your safety.",
        suggestedFix = "Review the changes and confirm the sync to proceed, or investigate why so many files look deleted.",
        retryable = false,
    )

    class PathTraversalRejected(path: String) : AppError(
        code = "PATH_TRAVERSAL_REJECTED",
        userMessage = "\"$path\" was rejected because it would resolve outside the linked folder.",
        suggestedFix = "This is a safety check and cannot be overridden. Report this if you believe it's a mistake.",
        retryable = false,
    )

    class Unknown(detail: String?, cause: Throwable? = null) : AppError(
        code = "UNKNOWN_ERROR",
        userMessage = "Something went wrong during sync.",
        suggestedFix = "Check the logs for details and retry.",
        retryable = true,
        technicalDetail = detail,
        cause = cause,
    )
}
