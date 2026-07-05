package com.syncbridge.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncbridge.app.data.repository.AppTheme
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.NetworkRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageConnections: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    var showClearCredentialsConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("Appearance")
            ThemeDropdown(selected = settings.theme, onSelected = viewModel::setTheme)

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Connections")
            OutlinedButton(onClick = onManageConnections, modifier = Modifier.fillMaxWidth()) {
                Text("Manage saved connections")
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Security")
            SettingRow("App lock (biometric / device PIN)") {
                Switch(checked = settings.appLockEnabled, onCheckedChange = viewModel::setAppLockEnabled)
            }
            OutlinedButton(onClick = { showClearCredentialsConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear all saved credentials")
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Sync defaults")
            ConflictRuleDropdown(selected = settings.defaultConflictRule, onSelected = viewModel::setDefaultConflictRule)
            Spacer(modifier = Modifier.height(8.dp))
            NetworkRuleDropdown(selected = settings.defaultNetworkRule, onSelected = viewModel::setDefaultNetworkRule)

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Transfers")
            Text("Max parallel transfers: ${settings.maxParallelTransfers}")
            Slider(
                value = settings.maxParallelTransfers.toFloat(),
                onValueChange = { viewModel.setMaxParallelTransfers(it.toInt()) },
                valueRange = 1f..8f,
                steps = 6,
            )
            Text("Retry count: ${settings.defaultRetryCount}")
            Slider(
                value = settings.defaultRetryCount.toFloat(),
                onValueChange = { viewModel.setDefaultRetryCount(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9,
            )
            OutlinedTextField(
                value = settings.defaultTimeoutSeconds.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setDefaultTimeoutSeconds) },
                label = { Text("Timeout (seconds)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showClearCredentialsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCredentialsConfirm = false },
            title = { Text("Clear all saved credentials?") },
            text = { Text("Every saved password, private key, and passphrase will be permanently unrecoverable. Connections will remain but need new credentials entered.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllCredentials(); showClearCredentialsConfirm = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearCredentialsConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        control()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(selected: AppTheme, onSelected: (AppTheme) -> Unit) {
    DropdownField(AppTheme.entries.map { it.name }, selected.name) { onSelected(AppTheme.valueOf(it)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictRuleDropdown(selected: ConflictRule, onSelected: (ConflictRule) -> Unit) {
    DropdownField(ConflictRule.entries.map { it.name }, selected.name) { onSelected(ConflictRule.valueOf(it)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkRuleDropdown(selected: NetworkRule, onSelected: (NetworkRule) -> Unit) {
    DropdownField(NetworkRule.entries.map { it.name }, selected.name) { onSelected(NetworkRule.valueOf(it)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}
