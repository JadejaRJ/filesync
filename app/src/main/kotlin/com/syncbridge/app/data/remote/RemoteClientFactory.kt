package com.syncbridge.app.data.remote

import com.syncbridge.core.common.client.RemoteFileClient
import com.syncbridge.core.common.model.ConnectionConfig
import com.syncbridge.core.common.model.ProtocolType
import com.syncbridge.protocol.ftp.FtpRemoteFileClient
import com.syncbridge.protocol.sftp.SftpKnownHostsStore
import com.syncbridge.protocol.sftp.SftpRemoteFileClient
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the right [RemoteFileClient] implementation for a connection's protocol. Adding a new
 * protocol (WebDAV, FTPS as its own client, ...) means adding one branch here — nothing else changes. */
@Singleton
class RemoteClientFactory @Inject constructor(
    private val knownHostsStore: SftpKnownHostsStore,
) {
    fun create(config: ConnectionConfig): RemoteFileClient = when (config.protocol) {
        ProtocolType.SFTP -> SftpRemoteFileClient(config, knownHostsStore)
        ProtocolType.FTP, ProtocolType.FTPS -> FtpRemoteFileClient(config)
    }
}
