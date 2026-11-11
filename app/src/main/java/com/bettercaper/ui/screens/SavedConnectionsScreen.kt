package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bettercaper.data.SavedConnection
import com.bettercaper.ui.components.ScreenTitle
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState
import com.bettercaper.viewmodel.ConnectParams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedConnectionsScreen(
    connections: List<SavedConnection>,
    uiState: AppUiState = AppUiState(),
    onConnect: (ConnectParams) -> Unit,
    onUpsert: (SavedConnection) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    var editTarget by remember { mutableStateOf<SavedConnection?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog || editTarget != null) {
        ConnectionDialog(
            initial = editTarget,
            onDismiss = { showAddDialog = false; editTarget = null },
            onSave = { conn ->
                onUpsert(conn)
                showAddDialog = false
                editTarget = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Saved Connections", uiState) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = GreenPrimary,
                contentColor = BackgroundDark
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add connection")
            }
        }
    ) { padding ->
        if (connections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = OnSurfaceMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No saved connections.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap + to add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(connections, key = { it.id }) { conn ->
                    SavedConnectionCard(
                        conn = conn,
                        onConnect = {
                            onConnect(
                                ConnectParams(
                                    sshHost = conn.sshHost,
                                    sshPort = conn.sshPort,
                                    sshUsername = conn.sshUsername,
                                    sshPassword = conn.sshPassword,
                                    sshKey = conn.sshKey,
                                    remoteApiPort = conn.remoteApiPort,
                                    apiUsername = conn.apiUsername,
                                    apiPassword = conn.apiPassword,
                                    defaultIface = conn.defaultIface,
                                    savedConnectionId = conn.id
                                )
                            )
                        },
                        onEdit = { editTarget = conn },
                        onDelete = { onDelete(conn.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedConnectionCard(
    conn: SavedConnection,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${conn.label}\"?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conn.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${conn.sshUsername}@${conn.sshHost}:${conn.sshPort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
                if (!conn.sshKey.isNullOrBlank()) {
                    Text(
                        "auth: key",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanAccent
                    )
                }
                if (!conn.defaultIface.isNullOrBlank()) {
                    Text(
                        "iface: ${conn.defaultIface}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanAccent
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OnSurfaceMuted)
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
            }
            IconButton(onClick = onConnect) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Connect", tint = GreenPrimary)
            }
        }
    }
}

@Composable
private fun ConnectionDialog(
    initial: SavedConnection?,
    onDismiss: () -> Unit,
    onSave: (SavedConnection) -> Unit
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var sshHost by remember { mutableStateOf(initial?.sshHost ?: "") }
    var sshPort by remember { mutableStateOf(initial?.sshPort?.toString() ?: "22") }
    var sshUser by remember { mutableStateOf(initial?.sshUsername ?: "") }
    var sshPass by remember { mutableStateOf(initial?.sshPassword ?: "") }
    var sshKey by remember { mutableStateOf(initial?.sshKey ?: "") }
    var useSshKey by remember { mutableStateOf(!(initial?.sshKey?.isNullOrBlank() ?: true)) }
    var apiPort by remember { mutableStateOf(initial?.remoteApiPort?.toString() ?: "8081") }
    var apiUser by remember { mutableStateOf(initial?.apiUsername ?: "user") }
    var apiPass by remember { mutableStateOf(initial?.apiPassword ?: "user") }
    var defaultIface by remember { mutableStateOf(initial?.defaultIface ?: "") }
    var sshPassVisible by remember { mutableStateOf(false) }
    var apiPassVisible by remember { mutableStateOf(false) }

    val canSave = label.isNotBlank() && sshHost.isNotBlank() && sshUser.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Connection" else "Edit Connection") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                DialogSectionLabel("SSH")

                OutlinedTextField(
                    value = sshHost,
                    onValueChange = { sshHost = it },
                    label = { Text("Host / IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sshPort,
                        onValueChange = { sshPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sshUser,
                        onValueChange = { sshUser = it },
                        label = { Text("User") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use SSH Key", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = useSshKey, onCheckedChange = { useSshKey = it })
                }
                
                if (useSshKey) {
                    OutlinedTextField(
                        value = sshKey,
                        onValueChange = { sshKey = it },
                        label = { Text("Private Key (PEM/OpenSSH)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = sshPass,
                        onValueChange = { sshPass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (sshPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { sshPassVisible = !sshPassVisible }) {
                                Icon(
                                    if (sshPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DialogSectionLabel("Interface (optional)")

                OutlinedTextField(
                    value = defaultIface,
                    onValueChange = { defaultIface = it },
                    label = { Text("Default Interface (e.g. wlan0)") },
                    placeholder = { Text("leave blank to prompt on connect", color = OnSurfaceMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                DialogSectionLabel("Bettercap API")

                OutlinedTextField(
                    value = apiPort,
                    onValueChange = { apiPort = it },
                    label = { Text("API Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = apiUser,
                        onValueChange = { apiUser = it },
                        label = { Text("API User") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = apiPass,
                        onValueChange = { apiPass = it },
                        label = { Text("API Password") },
                        singleLine = true,
                        visualTransformation = if (apiPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiPassVisible = !apiPassVisible }) {
                                Icon(
                                    if (apiPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        SavedConnection(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            label = label.trim(),
                            sshHost = sshHost.trim(),
                            sshPort = sshPort.toIntOrNull() ?: 22,
                            sshUsername = sshUser.trim(),
                            sshPassword = if (!useSshKey) sshPass else "",
                            sshKey = if (useSshKey) sshKey.trim() else null,
                            remoteApiPort = apiPort.toIntOrNull() ?: 8081,
                            apiUsername = apiUser.trim(),
                            apiPassword = apiPass,
                            defaultIface = defaultIface.trim().ifBlank { null }
                        )
                    )
                },
                enabled = canSave
            ) {
                Text("Save", color = if (canSave) GreenPrimary else OnSurfaceMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DialogSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = OnSurfaceMuted,
        modifier = Modifier.padding(top = 4.dp)
    )
}
