package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bettercaper.data.AirgeddonEvent
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AirgeddonUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirgeddonScreen(
    state: AirgeddonUiState,
    onCheckPlugin: () -> Unit,
    onDeployPlugin: () -> Unit,
    onRemovePlugin: () -> Unit,
    onRefreshEvents: () -> Unit,
    onClearLog: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { onCheckPlugin() }

    LaunchedEffect(state.events.size) {
        if (state.events.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Airgeddon Hooks", color = GreenPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.events.isNotEmpty()) {
                        IconButton(onClick = onClearLog) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear log", tint = OnSurfaceMuted)
                        }
                    }
                    IconButton(onClick = onRefreshEvents) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh events")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                PluginStatusCard(
                    deployed = state.pluginDeployed,
                    loading = state.loading,
                    error = state.error,
                    onDeploy = onDeployPlugin,
                    onRemove = onRemovePlugin
                )
            }

            if (state.events.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SettingsInputAntenna,
                                contentDescription = null,
                                tint = OnSurfaceMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (state.pluginDeployed)
                                    "No events yet. Run airgeddon with the plugin loaded."
                                else
                                    "Deploy the plugin, then run airgeddon.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceMuted
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Events (${state.events.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = GreenPrimary
                        )
                    }
                }
                items(state.events, key = { "${it.timestamp}_${it.category}_${it.detail}" }) { event ->
                    AirgeddonEventCard(event)
                }
            }
        }
    }
}

@Composable
private fun PluginStatusCard(
    deployed: Boolean,
    loading: Boolean,
    error: String?,
    onDeploy: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = if (deployed) GreenPrimary else OnSurfaceMuted,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Hook Plugin",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (deployed) "Deployed — restart airgeddon to activate"
                        else "Not deployed",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deployed) GreenVariant else OnSurfaceMuted
                    )
                }
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = GreenPrimary
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDeploy,
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPrimary,
                        contentColor = BackgroundDark
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (deployed) "Redeploy" else "Deploy")
                }
                OutlinedButton(
                    onClick = onRemove,
                    enabled = !loading && deployed,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Plugin path: ~/.airgeddon/plugins/bettercaper.sh",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun AirgeddonEventCard(event: AirgeddonEvent) {
    val categoryColor = categoryColor(event.category)

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = categoryColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        event.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    event.timestamp.take(19).replace("T", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                event.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun categoryColor(category: String): Color = when (category) {
    "HANDSHAKE" -> GreenPrimary
    "EVIL_TWIN" -> ErrorRed
    "DEAUTH" -> TagOrange
    "CRACK" -> WarningAmber
    "SCAN" -> CyanAccent
    "IFACE" -> TagBlue
    "MENU" -> OnSurfaceMuted
    "PLUGIN" -> TagPurple
    else -> OnSurfaceMuted
}
