package com.syncbridge.app.ui.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteFolderBrowserScreen(
    onBack: () -> Unit,
    onFolderSelected: (String) -> Unit,
    viewModel: RemoteFolderBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose remote folder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) { Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder") }
                    IconButton(onClick = { viewModel.navigateTo(state.currentPath) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { onFolderSelected(state.currentPath) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Select \"${state.currentPath}\"")
            }
        },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                BreadcrumbBar(path = state.currentPath, onUp = viewModel::navigateUp)

                when {
                    state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                    }
                    else -> LazyColumn {
                        items(state.entries, key = { it.path }) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.name) },
                                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                modifier = Modifier.clickable(onClick = { viewModel.navigateTo(entry.path) }),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("Folder name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) viewModel.createFolder(newFolderName)
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BreadcrumbBar(path: String, onUp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowBack, contentDescription = "Up one level") }
        Text(path, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
