package com.syncbridge.app.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.syncbridge.app.sync.SyncForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailsScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenLiveSync: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit,
    viewModel: ProfileDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val profile = state.profile ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { onEdit(profile.id) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Remote server: ${state.connectionName}", style = MaterialTheme.typography.bodyLarge)
            Text("Remote path: ${profile.remotePath}", style = MaterialTheme.typography.bodyMedium)
            Text("Sync mode: ${profile.syncMode}", style = MaterialTheme.typography.bodyMedium)
            Text("Conflict rule: ${profile.conflictRule}", style = MaterialTheme.typography.bodyMedium)
            Text("Delete rule: ${profile.deleteRule}", style = MaterialTheme.typography.bodyMedium)
            Text("Schedule: ${profile.scheduleRule.kind}", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Last sync: ${profile.lastSyncStatus ?: "Never"}", style = MaterialTheme.typography.bodyMedium)
            Text("Files synced (total): ${profile.totalFilesSynced}", style = MaterialTheme.typography.bodyMedium)
            Text("Uploaded: ${profile.totalUploadBytes / 1024} KB · Downloaded: ${profile.totalDownloadBytes / 1024} KB", style = MaterialTheme.typography.bodyMedium)
            Text("Errors (total): ${profile.totalErrors}", style = MaterialTheme.typography.bodyMedium)

            state.liveProgress?.let { live ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Syncing… ${live.filesCompleted}/${live.totalOperations}", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { live.progressFraction }, modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    SyncForegroundService.start(context, profile.id)
                    onOpenLiveSync(profile.id)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync now", modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { onOpenHistory(profile.id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null)
                Text("Sync history", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete sync profile?") },
            text = { Text("This removes the link between the local and remote folders. No files are deleted.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteProfile() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}
