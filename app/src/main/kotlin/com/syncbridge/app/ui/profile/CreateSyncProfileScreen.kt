package com.syncbridge.app.ui.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.DeleteRule
import com.syncbridge.core.common.model.ScheduleRule
import com.syncbridge.core.common.model.SyncMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSyncProfileScreen(
    pickedRemotePath: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPickRemoteFolder: (connectionId: Long) -> Unit,
    onAddConnection: () -> Unit,
    viewModel: CreateSyncProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val connections by viewModel.connections.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(pickedRemotePath) {
        pickedRemotePath?.let { viewModel.onRemotePathPicked(it) }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onLocalUriPicked(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "Create sync profile" else "Edit sync profile") },
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Profile name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            SectionLabel("Local folder")
            OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(state.localUri?.let { "Selected: ${it.substringAfterLast('/')}" } ?: "Choose local folder")
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Remote connection")
            if (connections.isEmpty()) {
                OutlinedButton(onClick = onAddConnection, modifier = Modifier.fillMaxWidth()) { Text("Add a connection first") }
            } else {
                ConnectionDropdown(
                    connections = connections,
                    selectedId = state.connectionId,
                    onSelected = viewModel::onConnectionSelected,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { state.connectionId?.let(onPickRemoteFolder) },
                    enabled = state.connectionId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(state.remotePath.ifBlank { "Choose remote folder" })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Sync mode")
            SyncModeDropdown(selected = state.syncMode, onSelected = viewModel::onSyncModeChange)

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Conflict rule")
            ConflictRuleDropdown(selected = state.conflictRule, onSelected = viewModel::onConflictRuleChange)

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Delete behavior")
            DeleteRuleDropdown(selected = state.deleteRule, onSelected = viewModel::onDeleteRuleChange)

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Schedule")
            ScheduleDropdown(selected = state.scheduleKind, onSelected = viewModel::onScheduleKindChange)

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Wi-Fi only", modifier = Modifier.weight(1f))
                Switch(checked = state.requireWifi, onCheckedChange = viewModel::onRequireWifiChange)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Only while charging", modifier = Modifier.weight(1f))
                Switch(checked = state.requireCharging, onCheckedChange = viewModel::onRequireChargingChange)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save sync profile")
            }
        }
    }

    if (state.showMirrorWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMirrorWarning,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Mirror mode warning") },
            text = { Text("Mirror mode makes the destination an exact copy of the source, deleting files that don't exist on the source. This cannot be undone unless a recycle bin is enabled.") },
            confirmButton = { TextButton(onClick = viewModel::dismissMirrorWarning) { Text("I understand") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionDropdown(
    connections: List<com.syncbridge.app.data.repository.ConnectionSummary>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = connections.firstOrNull { it.id == selectedId }?.name ?: "Select a connection"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            connections.forEach { connection ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(connection.name) },
                    onClick = { onSelected(connection.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncModeDropdown(selected: SyncMode, onSelected: (SyncMode) -> Unit) {
    val labels = mapOf(
        SyncMode.ONE_WAY_UPLOAD to "One-way upload (local → remote)",
        SyncMode.ONE_WAY_DOWNLOAD to "One-way download (remote → local)",
        SyncMode.TWO_WAY to "Two-way sync",
        SyncMode.MIRROR_LOCAL_TO_REMOTE to "Mirror: local → remote",
        SyncMode.MIRROR_REMOTE_TO_LOCAL to "Mirror: remote → local",
    )
    GenericDropdown(labels.values.toList(), labels.getValue(selected)) { pickedLabel ->
        onSelected(labels.entries.first { it.value == pickedLabel }.key)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictRuleDropdown(selected: ConflictRule, onSelected: (ConflictRule) -> Unit) {
    GenericDropdown(ConflictRule.entries.map { it.name }, selected.name) { picked ->
        onSelected(ConflictRule.valueOf(picked))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteRuleDropdown(selected: DeleteRule, onSelected: (DeleteRule) -> Unit) {
    GenericDropdown(DeleteRule.entries.map { it.name }, selected.name) { picked ->
        onSelected(DeleteRule.valueOf(picked))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDropdown(selected: ScheduleRule.ScheduleKind, onSelected: (ScheduleRule.ScheduleKind) -> Unit) {
    GenericDropdown(ScheduleRule.ScheduleKind.entries.map { it.name }, selected.name) { picked ->
        onSelected(ScheduleRule.ScheduleKind.valueOf(picked))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenericDropdown(options: List<String>, selected: String, onSelected: (String) -> Unit) {
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
