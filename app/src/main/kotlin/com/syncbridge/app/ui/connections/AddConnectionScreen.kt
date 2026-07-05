package com.syncbridge.app.ui.connections

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncbridge.core.common.model.ProtocolType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConnectionScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddConnectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "Add connection" else "Edit connection") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Connection name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            ProtocolDropdown(selected = state.protocol, onSelected = viewModel::onProtocolChange)

            if (state.isInsecureFtp) {
                Spacer(modifier = Modifier.height(8.dp))
                InsecureFtpWarning()
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = viewModel::onHostChange,
                    label = { Text("Host / IP address") },
                    modifier = Modifier.weight(2f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.port,
                    onValueChange = viewModel::onPortChange,
                    label = { Text("Port") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.protocol == ProtocolType.SFTP) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Use private key instead of password", modifier = Modifier.weight(1f))
                    Switch(checked = state.useKeyAuth, onCheckedChange = viewModel::onUseKeyAuthChange)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (state.useKeyAuth && state.protocol == ProtocolType.SFTP) {
                OutlinedTextField(
                    value = state.privateKeyPem,
                    onValueChange = viewModel::onPrivateKeyChange,
                    label = { Text("Private key (PEM)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.passphrase,
                    onValueChange = viewModel::onPassphraseChange,
                    label = { Text("Key passphrase (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.remoteRootPath,
                onValueChange = viewModel::onRemoteRootChange,
                label = { Text("Remote root directory") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.protocol == ProtocolType.FTP) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Passive mode", modifier = Modifier.weight(1f))
                    Switch(checked = state.ftpPassiveMode, onCheckedChange = viewModel::onPassiveModeChange)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row {
                OutlinedTextField(
                    value = state.timeoutSeconds,
                    onValueChange = viewModel::onTimeoutChange,
                    label = { Text("Timeout (s)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.retryCount,
                    onValueChange = viewModel::onRetryCountChange,
                    label = { Text("Retry count") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Keep-alive", modifier = Modifier.weight(1f))
                Switch(checked = state.keepAlive, onCheckedChange = viewModel::onKeepAliveChange)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row {
                OutlinedButton(onClick = viewModel::testConnection, enabled = !state.isTesting) {
                    if (state.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    } else {
                        Text("Test connection")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = viewModel::save, modifier = Modifier.padding(start = 12.dp)) {
                    Text("Save connection")
                }
            }

            state.testResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    result.message,
                    color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolDropdown(selected: ProtocolType, onSelected: (ProtocolType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Protocol") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(ProtocolType.SFTP, ProtocolType.FTP).forEach { protocol ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(protocol.name) },
                    onClick = { onSelected(protocol); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun InsecureFtpWarning() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "FTP sends credentials and files without encryption. Use SFTP or FTPS whenever possible.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
