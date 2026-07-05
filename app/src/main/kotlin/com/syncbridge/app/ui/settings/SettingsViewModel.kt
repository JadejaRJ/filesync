package com.syncbridge.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncbridge.app.data.repository.AppSettings
import com.syncbridge.app.data.repository.AppSettingsRepository
import com.syncbridge.app.data.repository.AppTheme
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.NetworkRule
import com.syncbridge.core.security.CredentialCipher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val credentialCipher: CredentialCipher,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    fun setDefaultConflictRule(rule: ConflictRule) = viewModelScope.launch { settingsRepository.setDefaultConflictRule(rule) }
    fun setDefaultNetworkRule(rule: NetworkRule) = viewModelScope.launch { settingsRepository.setDefaultNetworkRule(rule) }
    fun setMaxParallelTransfers(value: Int) = viewModelScope.launch { settingsRepository.setMaxParallelTransfers(value) }
    fun setDefaultRetryCount(value: Int) = viewModelScope.launch { settingsRepository.setDefaultRetryCount(value) }
    fun setDefaultTimeoutSeconds(value: Int) = viewModelScope.launch { settingsRepository.setDefaultTimeoutSeconds(value) }

    /** Destroys the Keystore key backing every stored connection secret, making all of them permanently unrecoverable. */
    fun clearAllCredentials() = credentialCipher.deleteKey()
}
