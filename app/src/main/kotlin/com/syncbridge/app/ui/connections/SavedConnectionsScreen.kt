package com.syncbridge.app.ui.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncbridge.app.data.repository.ConnectionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedConnectionsScreen(
    onBack: () -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (Long) -> Unit,
    viewModel: SavedConnectionsViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsState()
    val testingId by viewModel.testingId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved connections") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddConnection) { Icon(Icons.Filled.Add, contentDescription = "Add connection") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(connections, key = { it.id }) { connection ->
                ConnectionCard(
                    connection = connection,
                    isTesting = testingId == connection.id,
                    onClick = { onEditConnection(connection.id) },
                    onTest = { viewModel.testConnection(connection.id) },
                    onDuplicate = { viewModel.duplicate(connection.id) },
                    onDelete = { viewModel.delete(connection.id) },
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: ConnectionSummary,
    isTesting: Boolean,
    onClick: () -> Unit,
    onTest: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(connection.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (connection.isInsecure) {
                    Text("FTP (insecure)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("${connection.protocol.name}://${connection.host}:${connection.port}", style = MaterialTheme.typography.bodyMedium)
            connection.lastTestStatus?.let {
                Text("Last test: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    IconButton(onClick = onTest) { Icon(Icons.Filled.Wifi, contentDescription = "Test connection") }
                }
                IconButton(onClick = onDuplicate) { Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
    }
}
