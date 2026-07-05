package com.syncbridge.app.data.repository

import com.syncbridge.core.database.dao.KnownHostDao
import com.syncbridge.core.database.entity.KnownHostEntity
import com.syncbridge.protocol.sftp.SftpKnownHostsStore
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges `protocol:sftp`'s [SftpKnownHostsStore] contract to Room's [KnownHostDao]. */
@Singleton
class KnownHostsRepositoryImpl @Inject constructor(
    private val dao: KnownHostDao,
) : SftpKnownHostsStore {

    override suspend fun getStoredFingerprint(host: String, port: Int): String? = dao.find(host, port)?.fingerprint

    override suspend fun storeFingerprint(host: String, port: Int, fingerprint: String) {
        dao.upsert(KnownHostEntity(host = host, port = port, fingerprint = fingerprint, acceptedAt = System.currentTimeMillis()))
    }
}
