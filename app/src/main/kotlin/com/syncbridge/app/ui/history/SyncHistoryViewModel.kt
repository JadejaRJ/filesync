package com.syncbridge.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.data.repository.SyncHistoryRepository
import com.syncbridge.core.database.entity.SyncErrorEntity
import com.syncbridge.core.database.entity.SyncRunEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncHistoryUiState(
    val runs: List<SyncRunEntity> = emptyList(),
    val errors: List<SyncErrorEntity> = emptyList(),
)

@HiltViewModel
class SyncHistoryViewModel @Inject constructor(
    private val historyRepository: SyncHistoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val profileId: Long = checkNotNull(savedStateHandle["profileId"])

    val uiState: StateFlow<SyncHistoryUiState> = combine(
        historyRepository.observeRunsForProfile(profileId),
        historyRepository.observeErrorsForProfile(profileId),
    ) { runs, errors -> SyncHistoryUiState(runs, errors) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncHistoryUiState())

    fun clearHistory() = viewModelScope.launch { historyRepository.clearHistory(profileId) }
}
