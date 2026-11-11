package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bettercaper.ui.components.ScreenTitle
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState
import com.bettercaper.viewmodel.WifiBleUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiBleScreen(
    state: WifiBleUiState,
    uiState: AppUiState = AppUiState(),
    onCheck: () -> Unit,
    onDeploy: () -> Unit,
    onRemove: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { onCheck() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("WiFi & BLE Hook", uiState) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.error?.let {
                Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (state.capletDeployed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (state.capletDeployed) GreenPrimary else OnSurfaceMuted
                        )
                        Text(
                            if (state.capletDeployed) "Caplet deployed" else "Caplet not deployed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.capletDeployed) GreenPrimary else OnSurfaceMuted
                        )
                    }
                    Text(
                        "Deploys a caplet to ~/.bettercap/bettercaper-local.cap on the remote host. Scanning only starts when you trigger it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.capletDeployed) {
                            Button(
                                onClick = onDeploy,
                                enabled = !state.loading,
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                            ) {
                                Text("Deploy", color = BackgroundDark)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onRemove,
                                enabled = !state.loading
                            ) {
                                Text("Remove", color = ErrorRed)
                            }
                        }
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GreenPrimary, strokeWidth = 2.dp)
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (state.scanning) Icons.Default.Sensors else Icons.Default.SensorsOff,
                            contentDescription = null,
                            tint = if (state.scanning) CyanAccent else OnSurfaceMuted
                        )
                        Text(
                            if (state.scanning) "Scanning active" else "Scanning stopped",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.scanning) CyanAccent else OnSurfaceMuted
                        )
                    }
                    Text(
                        "Starts wifi.recon and ble.recon together. Results appear in the WiFi and Bluetooth screens. Events stream live in the Events tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                    if (state.capletDeployed) {
                        if (!state.scanning) {
                            Button(
                                onClick = onStartScan,
                                enabled = !state.loading,
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Text("Start Scan", color = BackgroundDark)
                            }
                        } else {
                            OutlinedButton(onClick = onStopScan, enabled = !state.loading) {
                                Text("Stop Scan", color = CyanAccent)
                            }
                        }
                    } else {
                        Text("Deploy the caplet first.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                    }
                }
            }
        }
    }
}
