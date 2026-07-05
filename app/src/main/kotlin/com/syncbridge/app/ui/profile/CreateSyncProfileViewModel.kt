package com.syncbridge.app.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.data.repository.ConnectionRepository
import com.syncbridge.app.data.repository.ConnectionSummary
import com.syncbridge.app.data.repository.SyncProfileRepository
import com.syncbridge.app.sync.SyncScheduler
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.DeleteRule
import com.syncbridge.core.common.model.NetworkRule
import com.syncbridge.core.common.model.ScheduleRule
import com.syncbridge.core.common.model.SyncMode
import com.syncbridge.core.common.model.SyncProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateSyncProfileUiState(
    val id: Long = 0,
    val name: String = "",
    val localUri: String? = null,
    val connectionId: Long? = null,
    val remotePath: String = "",
    val syncMode: SyncMode = SyncMode.ONE_WAY_UPLOAD,
    val conflictRule: ConflictRule = ConflictRule.KEEP_NEWEST,
    val deleteRule: DeleteRule = DeleteRule.NEVER_DELETE,
    val scheduleKind: ScheduleRule.ScheduleKind = ScheduleRule.ScheduleKind.EVERY_30_MIN,
    val requireWifi: Boolean = true,
    val requireCharging: Boolean = false,
    val showMirrorWarning: Boolean = false,
    val saved: Boolean = false,
) {
    val isMirrorMode: Boolean get() = syncMode == SyncMode.MIRROR_LOCAL_TO_REMOTE || syncMode == SyncMode.MIRROR_REMOTE_TO_LOCAL
    val canSave: Boolean get() = name.isNotBlank() && localUri != null && connectionId != null
}

@HiltViewModel
class CreateSyncProfileViewModel @Inject constructor(
    private val syncProfileRepository: SyncProfileRepository,
    connectionRepository: ConnectionRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val profileId: Long? = savedStateHandle.get<Long>("profileId")?.takeIf { it > 0 }

    val connections: StateFlow<List<ConnectionSummary>> = connectionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(CreateSyncProfileUiState())
    val state: StateFlow<CreateSyncProfileUiState> = _state.asStateFlow()

    init {
        profileId?.let { id ->
            viewModelScope.launch {
                syncProfileRepository.getById(id)?.let { p ->
                    _state.update {
                        it.copy(
                            id = p.id,
                            name = p.name,
                            localUri = p.localUri,
                            connectionId = p.remoteConnectionId,
                            remotePath = p.remotePath,
                            syncMode = p.syncMode,
                            conflictRule = p.conflictRule,
                            deleteRule = p.deleteRule,
                            scheduleKind = p.scheduleRule.kind,
                            requireWifi = p.scheduleRule.requireWifi,
                            requireCharging = p.scheduleRule.requireCharging,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onLocalUriPicked(uri: String) = _state.update { it.copy(localUri = uri) }
    fun onConnectionSelected(id: Long) = _state.update { it.copy(connectionId = id) }
    fun onRemotePathPicked(path: String) = _state.update { it.copy(remotePath = path) }

    fun onSyncModeChange(mode: SyncMode) = _state.update {
        it.copy(syncMode = mode, showMirrorWarning = mode == SyncMode.MIRROR_LOCAL_TO_REMOTE || mode == SyncMode.MIRROR_REMOTE_TO_LOCAL)
    }

    fun onConflictRuleChange(rule: ConflictRule) = _state.update { it.copy(conflictRule = rule) }
    fun onDeleteRuleChange(rule: DeleteRule) = _state.update { it.copy(deleteRule = rule) }
    fun onScheduleKindChange(kind: ScheduleRule.ScheduleKind) = _state.update { it.copy(scheduleKind = kind) }
    fun onRequireWifiChange(value: Boolean) = _state.update { it.copy(requireWifi = value) }
    fun onRequireChargingChange(value: Boolean) = _state.update { it.copy(requireCharging = value) }
    fun dismissMirrorWarning() = _state.update { it.copy(showMirrorWarning = false) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val profile = SyncProfile(
                id = s.id,
                name = s.name,
                localUri = s.localUri!!,
                remoteConnectionId = s.connectionId!!,
                remotePath = s.remotePath.ifBlank { "/" },
                syncMode = s.syncMode,
                conflictRule = s.conflictRule,
                deleteRule = s.deleteRule,
                scheduleRule = ScheduleRule(kind = s.scheduleKind, requireCharging = s.requireCharging, requireWifi = s.requireWifi),
                networkRule = if (s.requireWifi) NetworkRule.WIFI_ONLY else NetworkRule.ANY_NETWORK,
            )
            val id = syncProfileRepository.save(profile)
            syncScheduler.scheduleProfile(profile.copy(id = id))
            _state.update { it.copy(id = id, saved = true) }
        }
    }
}
