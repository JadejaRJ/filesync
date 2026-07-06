package com.syncbridge.app.ui.connections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.data.remote.RemoteClientFactory
import com.syncbridge.app.data.repository.ConnectionRepository
import com.syncbridge.core.common.client.ConnectionTestResult
import com.syncbridge.core.common.model.ConnectionConfig
import com.syncbridge.core.common.model.HostKeyVerification
import com.syncbridge.core.common.model.ProtocolType
import com.syncbridge.core.common.error.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddConnectionUiState(
    val id: Long = 0,
    val name: String = "",
    val protocol: ProtocolType = ProtocolType.SFTP,
    val host: String = "",
    val port: String = ConnectionConfig.DEFAULT_SFTP_PORT.toString(),
    val username: String = "",
    val password: String = "",
    val privateKeyPem: String = "",
    val passphrase: String = "",
    val useKeyAuth: Boolean = false,
    val remoteRootPath: String = "/",
    val timeoutSeconds: String = "30",
    val retryCount: String = "3",
    val keepAlive: Boolean = true,
    val ftpPassiveMode: Boolean = true,
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val saved: Boolean = false,
) {
    val isInsecureFtp: Boolean get() = protocol == ProtocolType.FTP
}

@HiltViewModel
class AddConnectionViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val remoteClientFactory: RemoteClientFactory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(AddConnectionUiState())
    val state: StateFlow<AddConnectionUiState> = _state.asStateFlow()

    init {
        val connectionId = savedStateHandle.get<Long>("connectionId")?.takeIf { it > 0 }
        if (connectionId != null) {
            viewModelScope.launch {
                connectionRepository.getConfig(connectionId)?.let { config ->
                    _state.update {
                        it.copy(
                            id = config.id,
                            name = config.name,
                            protocol = config.protocol,
                            host = config.host,
                            port = config.port.toString(),
                            username = config.username,
                            useKeyAuth = config.privateKeyPem != null,
                            remoteRootPath = config.remoteRootPath,
                            timeoutSeconds = config.timeoutSeconds.toString(),
                            retryCount = config.retryCount.toString(),
                            keepAlive = config.keepAlive,
                            ftpPassiveMode = config.ftpPassiveMode,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onProtocolChange(value: ProtocolType) = _state.update {
        it.copy(protocol = value, port = if (value == ProtocolType.SFTP) "22" else "21")
    }
    fun onHostChange(value: String) = _state.update { it.copy(host = value) }
    fun onPortChange(value: String) = _state.update { it.copy(port = value.filter(Char::isDigit)) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onPrivateKeyChange(value: String) = _state.update { it.copy(privateKeyPem = value) }
    fun onPassphraseChange(value: String) = _state.update { it.copy(passphrase = value) }
    fun onUseKeyAuthChange(value: Boolean) = _state.update { it.copy(useKeyAuth = value) }
    fun onRemoteRootChange(value: String) = _state.update { it.copy(remoteRootPath = value) }
    fun onTimeoutChange(value: String) = _state.update { it.copy(timeoutSeconds = value.filter(Char::isDigit)) }
    fun onRetryCountChange(value: String) = _state.update { it.copy(retryCount = value.filter(Char::isDigit)) }
    fun onKeepAliveChange(value: Boolean) = _state.update { it.copy(keepAlive = value) }
    fun onPassiveModeChange(value: Boolean) = _state.update { it.copy(ftpPassiveMode = value) }

    private fun buildConfig(): ConnectionConfig {
        val s = _state.value
        return ConnectionConfig(
            id = s.id,
            name = s.name,
            protocol = s.protocol,
            host = s.host,
            port = s.port.toIntOrNull() ?: if (s.protocol == ProtocolType.SFTP) 22 else 21,
            username = s.username,
            password = s.password.ifBlank { null },
            privateKeyPem = if (s.useKeyAuth) s.privateKeyPem.ifBlank { null } else null,
            passphrase = if (s.useKeyAuth) s.passphrase.ifBlank { null } else null,
            remoteRootPath = s.remoteRootPath.ifBlank { "/" },
            timeoutSeconds = s.timeoutSeconds.toIntOrNull() ?: 30,
            retryCount = s.retryCount.toIntOrNull() ?: 3,
            keepAlive = s.keepAlive,
            hostKeyVerification = HostKeyVerification.StrictKnownHosts,
            ftpPassiveMode = s.ftpPassiveMode,
        )
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, testResult = null) }
            val config = buildConfig()
            val client = remoteClientFactory.create(config)
            val result = try {
                client.testConnection()
            } catch (e: AppError) {
                ConnectionTestResult(success = false, message = e.userMessage)
            } catch (e: Exception) {
                ConnectionTestResult(success = false, message = e.message ?: "Could not connect to the server.")
            } finally {
                runCatching { client.close() }
            }
            _state.update { it.copy(isTesting = false, testResult = result) }
        }
    }

    fun save() {
        viewModelScope.launch {
            val id = connectionRepository.save(buildConfig())
            _state.update { it.copy(id = id, saved = true) }
        }
    }
}
