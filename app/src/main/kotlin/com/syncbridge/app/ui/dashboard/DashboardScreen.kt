package com.syncbridge.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncbridge.core.common.model.SyncMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddProfile: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SyncBridge") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfile) {
                Icon(Icons.Filled.Add, contentDescription = "Add sync profile")
            }
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyDashboard(modifier = Modifier.padding(padding), onAddProfile = onAddProfile)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.profile.id }) { item ->
                    val liveState = state.liveProgress?.takeIf { it.profileId == item.profile.id }
                    ProfileCard(
                        item = item,
                        liveProgressFraction = liveState?.progressFraction,
                        onClick = { onOpenProfile(item.profile.id) },
                        onToggle = { enabled -> viewModel.toggleEnabled(item.profile.id, enabled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboard(modifier: Modifier = Modifier, onAddProfile: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text("No sync profiles yet", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Link a local folder with a remote SFTP or FTP folder to get started.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddProfile) { Text("Create sync profile") }
    }
}

@Composable
private fun ProfileCard(
    item: DashboardItem,
    liveProgressFraction: Float?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SyncDirectionIcon(item.profile.syncMode)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.connectionName, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = item.profile.isEnabled, onCheckedChange = onToggle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Remote: ${item.profile.remotePath}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val statusText = item.profile.lastSyncStatus?.let { "Last sync: $it" } ?: "Never synced"
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (liveProgressFraction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(progress = { liveProgressFraction }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SyncDirectionIcon(mode: SyncMode) {
    val icon = when (mode) {
        SyncMode.ONE_WAY_UPLOAD, SyncMode.MIRROR_LOCAL_TO_REMOTE -> Icons.Filled.CloudUpload
        SyncMode.ONE_WAY_DOWNLOAD, SyncMode.MIRROR_REMOTE_TO_LOCAL -> Icons.Filled.CloudDownload
        SyncMode.TWO_WAY -> Icons.Filled.SwapVert
    }
    Icon(icon, contentDescription = mode.name, tint = MaterialTheme.colorScheme.primary)
}
