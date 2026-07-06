package com.syncbridge.app.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.data.remote.RemoteClientFactory
import com.syncbridge.app.data.repository.ConnectionRepository
import com.syncbridge.app.data.repository.ConnectionSummary
import com.syncbridge.core.common.error.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedConnectionsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val remoteClientFactory: RemoteClientFactory,
) : ViewModel() {

    val connections: StateFlow<List<ConnectionSummary>> = connectionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _testingId = MutableStateFlow<Long?>(null)
    val testingId: StateFlow<Long?> = _testingId.asStateFlow()

    fun testConnection(id: Long) {
        viewModelScope.launch {
            _testingId.value = id
            val config = connectionRepository.getConfig(id) ?: return@launch
            val client = remoteClientFactory.create(config)
            val result = try {
                client.testConnection()
            } catch (e: AppError) {
                com.syncbridge.core.common.client.ConnectionTestResult(false, e.userMessage)
            } catch (e: Exception) {
                com.syncbridge.core.common.client.ConnectionTestResult(false, e.message ?: "Could not connect.")
            } finally {
                runCatching { client.close() }
            }
            connectionRepository.recordTestResult(id, result.success)
            _testingId.value = null
        }
    }

    fun delete(id: Long) = viewModelScope.launch { connectionRepository.delete(id) }

    fun duplicate(id: Long) = viewModelScope.launch { connectionRepository.duplicate(id) }
}
