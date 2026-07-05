package com.syncbridge.protocol.sftp

/**
 * Persists the SHA/MD5 fingerprint SyncBridge has previously accepted for each SFTP host, so repeat
 * connections can be verified strictly instead of trusting-on-every-connect. Backed by Room in
 * `:core:database` (a `known_hosts` table keyed by host+port); deliberately separate from the OS's
 * OpenSSH known_hosts file since Android apps don't share that file.
 */
interface SftpKnownHostsStore {
    suspend fun getStoredFingerprint(host: String, port: Int): String?
    suspend fun storeFingerprint(host: String, port: Int, fingerprint: String)
}
