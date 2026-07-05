package com.syncbridge.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncbridge.core.database.entity.SyncErrorEntity
import com.syncbridge.core.database.entity.SyncRunEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHistoryScreen(
    onBack: () -> Unit,
    viewModel: SyncHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync history") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = viewModel::clearHistory) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear history") }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Runs (${state.runs.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Errors (${state.errors.size})") })
            }
            if (tab == 0) {
                RunsList(state.runs)
            } else {
                ErrorsList(state.errors)
            }
        }
    }
}

@Composable
private fun RunsList(runs: List<SyncRunEntity>) {
    if (runs.isEmpty()) {
        EmptyState("No sync runs yet")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(runs, key = { it.id }) { run ->
            Card {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
                    Text(DateFormat.getDateTimeInstance().format(Date(run.startedAt)), style = MaterialTheme.typography.bodyMedium)
                    Text("Status: ${run.status}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Up ${run.filesUploaded} · Down ${run.filesDownloaded} · Skipped ${run.filesSkipped} · Deleted ${run.filesDeleted} · Errors ${run.errorsCount}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Transferred: ${(run.bytesUploaded + run.bytesDownloaded) / 1024} KB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorsList(errors: List<SyncErrorEntity>) {
    if (errors.isEmpty()) {
        EmptyState("No errors recorded")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(errors, key = { it.id }) { error ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
                    Text(error.filePath, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("${error.errorCode}: ${error.errorMessage}", color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Fix: ${error.suggestedFix}", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}
