package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettercaper.api.model.BtDevice
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    uiState: AppUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val devices = uiState.session?.bluetooth?.devices ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth", color = GreenPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (devices.isNotEmpty()) {
                        Badge(containerColor = GreenPrimary, contentColor = BackgroundDark) {
                            Text("${devices.size}")
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = BleBlue,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No Bluetooth devices found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Run ble.recon on from the Command tab.",
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
                items(devices, key = { it.mac ?: it.name ?: devices.indexOf(it).toString() }) { device ->
                    BtDeviceCard(device)
                }
            }
        }
    }
}

@Composable
private fun BtDeviceCard(device: BtDevice) {
    val name = device.name?.takeIf { it.isNotBlank() } ?: "Unknown Device"
    val lastSeen = device.lastSeen?.take(19)?.replace("T", " ")

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = BleBlue,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    device.mac ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
                if (!device.vendor.isNullOrBlank()) {
                    Text(
                        device.vendor,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (lastSeen != null) {
                    Text(
                        lastSeen,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceMuted
                    )
                }
            }
            if (device.rssi != null) {
                Text(
                    "${device.rssi} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        device.rssi >= -60 -> GreenPrimary
                        device.rssi >= -75 -> WarningAmber
                        else -> ErrorRed
                    }
                )
            }
        }
    }
}
