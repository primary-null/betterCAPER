package com.bettercaper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettercaper.api.model.AccessPoint
import com.bettercaper.api.model.WifiClient
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(
    uiState: AppUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val aps = uiState.session?.wifi?.aps ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi", color = GreenPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (aps.isNotEmpty()) {
                        Badge(containerColor = GreenPrimary, contentColor = BackgroundDark) {
                            Text("${aps.size}")
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
        if (aps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = OnSurfaceMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No access points found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Run wifi.recon on from the Command tab.",
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
                items(aps, key = { it.mac ?: it.ssid ?: aps.indexOf(it).toString() }) { ap ->
                    ApCard(ap)
                }
            }
        }
    }
}

@Composable
private fun ApCard(ap: AccessPoint) {
    var expanded by remember { mutableStateOf(false) }
    val clients = ap.clients ?: emptyList()
    val ssid = ap.ssid?.takeIf { it.isNotBlank() } ?: "<Hidden>"

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = clients.isNotEmpty()) { expanded = !expanded }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = rssiColor(ap.rssi),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ssid,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        ap.mac ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    ap.rssi?.let {
                        Text(
                            "$it dBm",
                            style = MaterialTheme.typography.labelMedium,
                            color = rssiColor(it)
                        )
                    }
                    if (clients.isNotEmpty()) {
                        Text(
                            "${clients.size} client${if (clients.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ap.channel?.let { InfoChip("ch $it") }
                ap.encryption?.takeIf { it.isNotBlank() }?.let { enc ->
                    InfoChip(enc, color = encryptionColor(enc))
                }
                if (clients.isNotEmpty()) {
                    InfoChip(
                        if (expanded) "collapse" else "expand",
                        color = OnSurfaceMuted
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = SurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Clients",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    clients.forEach { client ->
                        ClientRow(client)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientRow(client: WifiClient) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = OnSurfaceMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                client.mac ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!client.vendor.isNullOrBlank()) {
                Text(
                    client.vendor,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        client.rssi?.let {
            Text(
                "$it dBm",
                style = MaterialTheme.typography.labelSmall,
                color = rssiColor(it)
            )
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color = SurfaceVariant) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color.takeIf { color != SurfaceVariant } ?: OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun rssiColor(rssi: Int?): Color = when {
    rssi == null -> OnSurfaceMuted
    rssi >= -50 -> GreenPrimary
    rssi >= -70 -> GreenVariant
    rssi >= -80 -> WarningAmber
    else -> ErrorRed
}

private fun encryptionColor(enc: String): Color = when {
    enc.contains("WPA3", ignoreCase = true) -> GreenPrimary
    enc.contains("WPA2", ignoreCase = true) -> GreenVariant
    enc.contains("WPA", ignoreCase = true) -> WarningAmber
    enc.contains("WEP", ignoreCase = true) -> ErrorRed
    enc.equals("OPEN", ignoreCase = true) || enc.isBlank() -> ErrorRed
    else -> OnSurfaceMuted
}
