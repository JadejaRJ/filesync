package com.syncbridge.app.ui.connections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.data.remote.RemoteClientFactory
import com.syncbridge.app.data.repository.ConnectionRepository
import com.syncbridge.core.common.client.RemoteFileClient
import com.syncbridge.core.common.error.AppError
import com.syncbridge.core.common.model.RemoteFileMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemoteBrowserUiState(
    val currentPath: String = "/",
    val entries: List<RemoteFileMetadata> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class RemoteFolderBrowserViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val remoteClientFactory: RemoteClientFactory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val connectionId: Long = checkNotNull(savedStateHandle["connectionId"])
    private var client: RemoteFileClient? = null
    private var rootPath: String = "/"

    private val _state = MutableStateFlow(RemoteBrowserUiState())
    val state: StateFlow<RemoteBrowserUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { connect() }
    }

    private suspend fun connect() {
        val config = connectionRepository.getConfig(connectionId)
        if (config == null) {
            _state.update { it.copy(isLoading = false, error = "Connection not found") }
            return
        }
        rootPath = config.remoteRootPath
        val newClient = remoteClientFactory.create(config)
        try {
            newClient.connect()
            client = newClient
            navigateTo(rootPath)
        } catch (e: AppError) {
            _state.update { it.copy(isLoading = false, error = e.userMessage) }
        }
    }

    fun navigateTo(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val entries = client?.listFiles(path).orEmpty()
                    .filter { it.isDirectory }
                    .sortedBy { it.name.lowercase() }
                _state.update { it.copy(currentPath = path, entries = entries, isLoading = false) }
            } catch (e: AppError) {
                _state.update { it.copy(isLoading = false, error = e.userMessage) }
            }
        }
    }

    fun navigateUp() {
        val current = _state.value.currentPath
        if (current == rootPath || current == "/") return
        navigateTo(current.substringBeforeLast('/').ifEmpty { "/" })
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val path = "${_state.value.currentPath.trimEnd('/')}/$name"
            try {
                client?.createFolder(path)
                navigateTo(_state.value.currentPath)
            } catch (e: AppError) {
                _state.update { it.copy(error = e.userMessage) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client?.close()
    }
}
