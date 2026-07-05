package com.syncbridge.app.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.data.repository.ConnectionRepository
import com.syncbridge.app.data.repository.SyncProfileRepository
import com.syncbridge.app.sync.LiveSyncState
import com.syncbridge.app.sync.SyncRunner
import com.syncbridge.app.sync.SyncScheduler
import com.syncbridge.core.common.model.SyncProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileDetailsUiState(
    val profile: SyncProfile? = null,
    val connectionName: String = "",
    val liveProgress: LiveSyncState? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class ProfileDetailsViewModel @Inject constructor(
    private val syncProfileRepository: SyncProfileRepository,
    private val connectionRepository: ConnectionRepository,
    private val syncScheduler: SyncScheduler,
    private val syncRunner: SyncRunner,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val profileId: Long = checkNotNull(savedStateHandle["profileId"])
    private val _deleted = MutableStateFlow(false)

    val uiState: StateFlow<ProfileDetailsUiState> = combine(
        syncProfileRepository.observeById(profileId),
        connectionRepository.observeAll(),
        syncRunner.liveProgress,
        _deleted,
    ) { profile, connections, live, deleted ->
        ProfileDetailsUiState(
            profile = profile,
            connectionName = connections.firstOrNull { it.id == profile?.remoteConnectionId }?.name ?: "",
            liveProgress = live?.takeIf { it.profileId == profileId },
            deleted = deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileDetailsUiState())

    fun deleteProfile() {
        viewModelScope.launch {
            uiState.value.profile?.let {
                syncScheduler.cancelProfile(it.id)
                syncProfileRepository.delete(it)
            }
            _deleted.value = true
        }
    }
}
