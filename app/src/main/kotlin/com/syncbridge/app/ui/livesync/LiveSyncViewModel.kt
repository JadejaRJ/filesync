package com.syncbridge.app.ui.livesync

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.sync.LiveSyncState
import com.syncbridge.app.sync.SyncRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LiveSyncViewModel @Inject constructor(
    private val syncRunner: SyncRunner,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val profileId: Long = checkNotNull(savedStateHandle["profileId"])

    val liveState: StateFlow<LiveSyncState?> = syncRunner.liveProgress
        .map { it?.takeIf { state -> state.profileId == profileId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun togglePause() {
        val current = syncRunner.liveProgress.value ?: return
        if (current.isPaused) syncRunner.resume(profileId) else syncRunner.pause(profileId)
    }
}
