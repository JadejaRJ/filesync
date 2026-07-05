package com.syncbridge.core.common.model

/**
 * Fully-resolved connection parameters handed to a [com.syncbridge.core.common.client.RemoteFileClient].
 * Secrets ([password], [privateKeyPem], [passphrase]) are decrypted just-in-time by the caller
 * (see core:security) and must never be persisted or logged in this form.
 */
data class ConnectionConfig(
    val id: Long = 0,
    val name: String,
    val protocol: ProtocolType,
    val host: String,
    val port: Int,
    val username: String,
    val password: String? = null,
    val privateKeyPem: String? = null,
    val passphrase: String? = null,
    val remoteRootPath: String = "/",
    val timeoutSeconds: Int = 30,
    val retryCount: Int = 3,
    val keepAlive: Boolean = true,
    // SFTP-only
    val hostKeyVerification: HostKeyVerification = HostKeyVerification.StrictKnownHosts,
    // FTP-only
    val ftpPassiveMode: Boolean = true,
    val ftpEncoding: String = "UTF-8",
    val useExplicitTls: Boolean = false, // FTPS, future-ready
) {
    companion object {
        const val DEFAULT_SFTP_PORT = 22
        const val DEFAULT_FTP_PORT = 21
    }

    /** True when this configuration is a plaintext-credential FTP connection and should surface a security warning. */
    val isInsecure: Boolean
        get() = protocol == ProtocolType.FTP && !useExplicitTls
}

/** How an SFTP client should verify the server's host key. */
sealed class HostKeyVerification {
    /** Verify against ~/.ssh/known_hosts-equivalent store maintained by the app; fail on mismatch or unknown host. */
    data object StrictKnownHosts : HostKeyVerification()

    /** Trust the given fingerprint once (used for "accept and remember" on first connect). */
    data class PinnedFingerprint(val sha256Fingerprint: String) : HostKeyVerification()

    /** Accept any host key. Strongly discouraged; only for explicit user opt-in in trusted test environments. */
    data object AcceptAny : HostKeyVerification()
}
