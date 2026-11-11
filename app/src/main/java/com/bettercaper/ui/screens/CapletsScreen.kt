package com.bettercaper.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bettercaper.ui.components.ScreenTitle
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState
import com.bettercaper.viewmodel.CapletsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapletsScreen(
    state: CapletsUiState,
    uiState: AppUiState = AppUiState(),
    onLoadCaplets: () -> Unit,
    onRunCaplet: (String) -> Unit,
    onEditCaplet: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onSaveCaplet: (String, String) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (state.caplets.isEmpty() && !state.loading) {
            onLoadCaplets()
        }
    }

    var showRunDialog by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Caplets", uiState) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onLoadCaplets) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, containerColor = GreenPrimary) {
                Icon(Icons.Default.Add, "New Caplet", tint = SurfaceDark)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.loading && state.caplets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null && state.caplets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Error loading caplets: ${state.error}", color = MaterialTheme.colorScheme.error)
                }
            } else if (state.caplets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No caplets found. Try refreshing.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.caplets) { caplet ->
                        val name = caplet.substringAfterLast("/")
                        ListItem(
                            headlineContent = { Text(name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(caplet, style = MaterialTheme.typography.bodySmall) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onEditCaplet(caplet) }) {
                                        Icon(Icons.Default.Edit, "Edit Caplet", tint = OnSurfaceMuted)
                                    }
                                    IconButton(onClick = { showRunDialog = caplet }) {
                                        Icon(Icons.Default.PlayArrow, "Run Caplet", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            modifier = Modifier.clickable { showRunDialog = caplet }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        if (state.editingCaplet != null && state.editingContent != null) {
            var editedContent by remember(state.editingContent) { mutableStateOf(state.editingContent) }
            Dialog(
                onDismissRequest = onCloseEditor,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = { Text("Editing ${state.editingCaplet.substringAfterLast("/")}", style = MaterialTheme.typography.titleMedium) },
                            navigationIcon = {
                                IconButton(onClick = onCloseEditor) { Icon(Icons.Default.Close, "Close") }
                            },
                            actions = {
                                TextButton(onClick = { onSaveCaplet(state.editingCaplet, editedContent) }) {
                                    Text("Save", color = GreenPrimary)
                                }
                            }
                        )
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { editedContent = it },
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = SurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            var newName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create New Caplet") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Caplet name (e.g. myalias.cap)") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCreateDialog = false
                        val path = if (newName.contains("/")) newName else "/usr/local/share/bettercap/caplets/$newName"
                        onSaveCaplet(path, "# New Caplet/Alias\n")
                        onLoadCaplets() // Refresh list
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showRunDialog != null) {
            AlertDialog(
                onDismissRequest = { showRunDialog = null },
                title = { Text("Run Caplet") },
                text = { Text("Are you sure you want to execute ${showRunDialog?.substringAfterLast("/")}?") },
                confirmButton = {
                    TextButton(onClick = {
                        showRunDialog?.let { onRunCaplet(it) }
                        showRunDialog = null
                    }) {
                        Text("Run")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRunDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
