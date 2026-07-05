package com.syncbridge.app.data.local

import android.content.Context
import android.net.Uri
import com.syncbridge.core.common.client.LocalFileSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafLocalFileSystemFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(treeUriString: String): LocalFileSystem = SafLocalFileSystem(context, Uri.parse(treeUriString))
}
