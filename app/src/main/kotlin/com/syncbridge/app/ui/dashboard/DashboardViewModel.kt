package com.syncbridge.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.data.repository.ConnectionRepository
import com.syncbridge.app.data.repository.SyncProfileRepository
import com.syncbridge.app.sync.LiveSyncState
import com.syncbridge.app.sync.SyncScheduler
import com.syncbridge.app.sync.SyncRunner
import com.syncbridge.core.common.model.SyncProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardItem(
    val profile: SyncProfile,
    val connectionName: String,
)

data class DashboardUiState(
    val items: List<DashboardItem> = emptyList(),
    val liveProgress: LiveSyncState? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncProfileRepository: SyncProfileRepository,
    connectionRepository: ConnectionRepository,
    private val syncScheduler: SyncScheduler,
    private val syncRunner: SyncRunner,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        syncProfileRepository.observeAll(),
        connectionRepository.observeAll(),
        syncRunner.liveProgress,
    ) { profiles, connections, live ->
        val namesById = connections.associate { it.id to it.name }
        DashboardUiState(
            items = profiles.map { DashboardItem(it, namesById[it.remoteConnectionId] ?: "Unknown server") },
            liveProgress = live,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun toggleEnabled(profileId: Long, enabled: Boolean) {
        viewModelScope.launch {
            syncProfileRepository.setEnabled(profileId, enabled)
            val profile = syncProfileRepository.getById(profileId) ?: return@launch
            if (enabled) syncScheduler.scheduleProfile(profile) else syncScheduler.cancelProfile(profileId)
        }
    }
}
