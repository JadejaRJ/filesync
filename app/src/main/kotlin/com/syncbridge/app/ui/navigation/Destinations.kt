package com.syncbridge.app.ui.navigation

object Destinations {
    const val DASHBOARD = "dashboard"
    const val SAVED_CONNECTIONS = "connections"
    const val ADD_CONNECTION = "connections/edit?connectionId={connectionId}"
    const val REMOTE_FOLDER_BROWSER = "connections/{connectionId}/browse"
    const val CREATE_SYNC_PROFILE = "profiles/edit?profileId={profileId}"
    const val PROFILE_DETAILS = "profiles/{profileId}"
    const val LIVE_SYNC = "profiles/{profileId}/live"
    const val SYNC_HISTORY = "profiles/{profileId}/history"
    const val SETTINGS = "settings"

    const val ARG_CONNECTION_ID = "connectionId"
    const val ARG_PROFILE_ID = "profileId"

    fun addConnection(connectionId: Long? = null) = "connections/edit?connectionId=${connectionId ?: -1}"
    fun remoteFolderBrowser(connectionId: Long) = "connections/$connectionId/browse"
    fun createSyncProfile(profileId: Long? = null) = "profiles/edit?profileId=${profileId ?: -1}"
    fun profileDetails(profileId: Long) = "profiles/$profileId"
    fun liveSync(profileId: Long) = "profiles/$profileId/live"
    fun syncHistory(profileId: Long) = "profiles/$profileId/history"

    /** Key used on [androidx.navigation.NavBackStackEntry.savedStateHandle] to return a picked remote path. */
    const val RESULT_REMOTE_PATH = "result_remote_path"

    /** Key used on the caller's savedStateHandle to return a picked local folder tree URI. */
    const val RESULT_LOCAL_URI = "result_local_uri"
}
