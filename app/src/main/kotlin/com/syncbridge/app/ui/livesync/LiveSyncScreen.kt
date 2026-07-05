package com.syncbridge.app.ui.livesync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncbridge.app.sync.SyncForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSyncScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: LiveSyncViewModel = hiltViewModel(),
) {
    val state by viewModel.liveState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.profileName ?: "Syncing") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (state == null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No sync currently running for this profile.")
            }
            return@Scaffold
        }

        val live = state!!
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(live.currentOperationLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(live.currentFile ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1)

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(progress = { live.progressFraction }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Text("${live.filesCompleted} / ${live.totalOperations} files")

            if (live.currentFileBytesTotal > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val filePct = live.currentFileBytesDone.toFloat() / live.currentFileBytesTotal
                LinearProgressIndicator(progress = { filePct }, modifier = Modifier.fillMaxWidth())
                Text("${live.currentFileBytesDone / 1024} KB / ${live.currentFileBytesTotal / 1024} KB")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Uploaded: ${live.bytesUploaded / 1024} KB")
            Text("Downloaded: ${live.bytesDownloaded / 1024} KB")
            Text("Errors: ${live.errors}", color = if (live.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)

            if (live.awaitingMassDeletionConfirmation) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "This sync detected an unusually large number of deletions and is paused for your confirmation.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.togglePause() }, modifier = Modifier.weight(1f)) {
                    Icon(if (live.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                    Text(if (live.isPaused) "Resume" else "Pause", modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = { SyncForegroundService.cancel(context, profileId) },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = null)
                    Text("Cancel", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
