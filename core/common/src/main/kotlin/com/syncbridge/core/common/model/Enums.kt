package com.syncbridge.core.common.model

/** Which remote protocol a [com.syncbridge.core.common.model.ConnectionConfig] speaks. */
enum class ProtocolType {
    SFTP,
    FTP,
    FTPS, // future-ready, not yet wired to a client implementation
}

/** Direction(s) in which a sync profile propagates changes. */
enum class SyncMode {
    ONE_WAY_UPLOAD,
    ONE_WAY_DOWNLOAD,
    TWO_WAY,
    MIRROR_LOCAL_TO_REMOTE,
    MIRROR_REMOTE_TO_LOCAL,
}

/** How the engine should resolve a file changed on both sides since the last sync. */
enum class ConflictRule {
    ASK_EVERY_TIME,
    KEEP_LOCAL,
    KEEP_REMOTE,
    KEEP_NEWEST,
    KEEP_BOTH_RENAME,
    SKIP,
    COMPARE_CHECKSUM,
}

/** How the engine should propagate deletions detected on one side. */
enum class DeleteRule {
    NEVER_DELETE,
    RECYCLE_BIN,
    MIRROR_DELETE,
    ASK_BEFORE_DELETE,
}

/** Recurrence for WorkManager-scheduled sync. [intervalMinutes] is only used when [kind] is CUSTOM. */
data class ScheduleRule(
    val kind: ScheduleKind,
    val intervalMinutes: Long = 30,
    val requireCharging: Boolean = false,
    val requireWifi: Boolean = true,
    val minBatteryPercent: Int? = null,
) {
    enum class ScheduleKind {
        MANUAL_ONLY,
        EVERY_15_MIN,
        EVERY_30_MIN,
        HOURLY,
        EVERY_6_HOURS,
        DAILY,
        CUSTOM,
    }

    fun effectiveIntervalMinutes(): Long = when (kind) {
        ScheduleKind.MANUAL_ONLY -> 0
        ScheduleKind.EVERY_15_MIN -> 15
        ScheduleKind.EVERY_30_MIN -> 30
        ScheduleKind.HOURLY -> 60
        ScheduleKind.EVERY_6_HOURS -> 360
        ScheduleKind.DAILY -> 1440
        ScheduleKind.CUSTOM -> intervalMinutes
    }
}

enum class NetworkRule {
    ANY_NETWORK,
    WIFI_ONLY,
    WIFI_OR_ETHERNET,
    UNMETERED_ONLY,
}

enum class SyncOperationType {
    UPLOAD,
    DOWNLOAD,
    DELETE_LOCAL,
    DELETE_REMOTE,
    CONFLICT,
    SKIP,
    CREATE_LOCAL_FOLDER,
    CREATE_REMOTE_FOLDER,
}

enum class SyncRunStatus {
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED,
    PAUSED,
}
