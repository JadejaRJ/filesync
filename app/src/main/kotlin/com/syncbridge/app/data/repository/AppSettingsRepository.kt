package com.syncbridge.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.NetworkRule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "syncbridge_settings")

enum class AppTheme { LIGHT, DARK, SYSTEM }

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val appLockEnabled: Boolean = false,
    val defaultConflictRule: ConflictRule = ConflictRule.KEEP_NEWEST,
    val defaultNetworkRule: NetworkRule = NetworkRule.WIFI_ONLY,
    val maxParallelTransfers: Int = 4,
    val defaultRetryCount: Int = 3,
    val defaultTimeoutSeconds: Int = 30,
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val CONFLICT_RULE = stringPreferencesKey("default_conflict_rule")
        val NETWORK_RULE = stringPreferencesKey("default_network_rule")
        val MAX_PARALLEL = intPreferencesKey("max_parallel_transfers")
        val RETRY_COUNT = intPreferencesKey("default_retry_count")
        val TIMEOUT = intPreferencesKey("default_timeout_seconds")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
            appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
            defaultConflictRule = prefs[Keys.CONFLICT_RULE]?.let { runCatching { ConflictRule.valueOf(it) }.getOrNull() } ?: ConflictRule.KEEP_NEWEST,
            defaultNetworkRule = prefs[Keys.NETWORK_RULE]?.let { runCatching { NetworkRule.valueOf(it) }.getOrNull() } ?: NetworkRule.WIFI_ONLY,
            maxParallelTransfers = prefs[Keys.MAX_PARALLEL] ?: 4,
            defaultRetryCount = prefs[Keys.RETRY_COUNT] ?: 3,
            defaultTimeoutSeconds = prefs[Keys.TIMEOUT] ?: 30,
        )
    }

    suspend fun setTheme(theme: AppTheme) = context.settingsDataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setAppLockEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.APP_LOCK] = enabled }
    suspend fun setDefaultConflictRule(rule: ConflictRule) = context.settingsDataStore.edit { it[Keys.CONFLICT_RULE] = rule.name }
    suspend fun setDefaultNetworkRule(rule: NetworkRule) = context.settingsDataStore.edit { it[Keys.NETWORK_RULE] = rule.name }
    suspend fun setMaxParallelTransfers(value: Int) = context.settingsDataStore.edit { it[Keys.MAX_PARALLEL] = value }
    suspend fun setDefaultRetryCount(value: Int) = context.settingsDataStore.edit { it[Keys.RETRY_COUNT] = value }
    suspend fun setDefaultTimeoutSeconds(value: Int) = context.settingsDataStore.edit { it[Keys.TIMEOUT] = value }
}
