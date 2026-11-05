package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.clickable
import com.bettercaper.api.model.BcModule
import com.bettercaper.api.model.Host
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: AppUiState,
    onRefresh: () -> Unit,
    onSelectInterface: (String) -> Unit,
    onDismissInterfaceDialog: () -> Unit,
    onNavigateEvents: () -> Unit,
    onNavigateCommand: () -> Unit,
    onNavigateWifi: () -> Unit,
    onNavigateBluetooth: () -> Unit,
    onNavigateModules: () -> Unit,
    onNavigateSpoofing: () -> Unit,
    onNavigateProxies: () -> Unit,
    onNavigateWebUi: () -> Unit,
    onNavigateAirgeddon: () -> Unit,
    onNavigateCaplets: () -> Unit,
    onNavigateWifiBle: () -> Unit,
    onNavigatePcaps: () -> Unit,
    onExecuteCommand: (String) -> Unit,
    onStartScan: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedHost by remember { mutableStateOf<Host?>(null) }
    val session = uiState.session
    val iface = session?.iface
    val gateway = session?.gateway
    val hosts = session?.lan?.hosts ?: emptyList()
    val modules = session?.modules ?: emptyList()
    val wifiReconModule = modules.firstOrNull { it.name == "wifi.recon" }
    val wifiReconRunning = wifiReconModule?.running == true
    @Suppress("UNCHECKED_CAST")
    val wifiIfaceName = (wifiReconModule?.parameters?.get("wifi.interface") as? Map<String, Any>)
        ?.get("value") as? String

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("betterCAPER", style = MaterialTheme.typography.titleLarge, color = GreenPrimary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (iface != null) {
                                Text(
                                    "${iface.name ?: "Unknown"} · ${iface.resolvedIpv4 ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceMuted
                                )
                            }
                            if (wifiReconRunning && wifiIfaceName != null && wifiIfaceName != "null") {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(wifiIfaceName, style = MaterialTheme.typography.labelSmall) },
                                    icon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = TagBlue.copy(alpha = 0.15f),
                                        labelColor = TagBlue,
                                        iconContentColor = TagBlue
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = TagBlue.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.height(20.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Modules") },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                onClick = { menuExpanded = false; onNavigateModules() }
                            )
                            DropdownMenuItem(
                                text = { Text("Spoofing & MAC") },
                                leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null) },
                                onClick = { menuExpanded = false; onNavigateSpoofing() }
                            )
                            DropdownMenuItem(
                                text = { Text("Proxies") },
                                leadingIcon = { Icon(Icons.Default.Http, contentDescription = null) },
                                onClick = { menuExpanded = false; onNavigateProxies() }
                            )
                            DropdownMenuItem(
                                text = { Text("Web UI") },
                                leadingIcon = { Icon(Icons.Default.Web, contentDescription = null) },
                                onClick = { menuExpanded = false; onNavigateWebUi() }
                            )
                            DropdownMenuItem(
                                text = { Text("Caplets") },
                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                                onClick = { menuExpanded = false; onNavigateCaplets() }
                            )
                            DropdownMenuItem(
                                text = { Text("WiFi & BLE Hook") },
                                leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null) },
                                onClick = { menuExpanded = false; onNavigateWifiBle() }
                            )
                            DropdownMenuItem(
                                text = { Text("Airgeddon Hooks") },
                                leadingIcon = { Icon(Icons.Default.SettingsInputAntenna, contentDescription = null) },
                                onClick = { menuExpanded = false; onNavigateAirgeddon() }
                            )
                            DropdownMenuItem(
                                text = { Text("PCAP Exports") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = { menuExpanded = false; onNavigatePcaps() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Disconnect", color = ErrorRed) },
                                leadingIcon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = ErrorRed) },
                                onClick = { menuExpanded = false; onDisconnect() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Hub, contentDescription = null) },
                    label = { Text("Hosts") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateWifi,
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    label = { Text("WiFi") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateBluetooth,
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = BleBlue) },
                    label = { Text("BLE") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateEvents,
                    icon = { Icon(Icons.Default.Stream, contentDescription = null) },
                    label = { Text("Events") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateCommand,
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("CMD") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (uiState.reconnectingMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = WarningAmber
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = uiState.reconnectingMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarningAmber
                            )
                        }
                    }
                }
            }
            // Interface card
            if (iface != null || gateway != null) {
                item {
                    DashCard(title = "Interface") {
                        iface?.let {
                            InfoRow("Interface", it.name ?: "—")
                            InfoRow("IPv4", it.resolvedIpv4 ?: "—")
                            InfoRow("MAC", it.mac ?: "—")
                        }
                        gateway?.let {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SurfaceVariant)
                            InfoRow("Gateway", "${it.ipv4 ?: "—"}  ${it.hostname ?: ""}")
                            InfoRow("GW MAC", it.mac ?: "—")
                        }
                    }
                }
            }

            // Active modules
            val activeModules = modules.filter { it.running == true }
            if (activeModules.isNotEmpty()) {
                item {
                    DashCard(title = "Active Modules (${activeModules.size})") {
                        activeModules.forEach { module ->
                            ModuleChip(module)
                        }
                    }
                }
            }

            // LAN hosts
            item {
                Text(
                    "LAN Hosts (${hosts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = GreenPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (hosts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No hosts discovered yet.\nRun net.probe on from the Command tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceMuted
                        )
                    }
                }
            } else {
                items(hosts, key = { it.mac ?: it.ipv4 ?: hosts.indexOf(it).toString() }) { host ->
                    HostCard(host, onClick = { selectedHost = host })
                }
            }
        }
    }

    if (selectedHost != null) {
        val host = selectedHost!!
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { selectedHost = null },
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    "Target: ${host.ipv4 ?: host.mac}", 
                    style = MaterialTheme.typography.titleLarge, 
                    color = GreenPrimary
                )
                Spacer(Modifier.height(8.dp))
                
                // Airgeddon / Utility Options
                Text("Airgeddon / Recon", style = MaterialTheme.typography.labelMedium, color = OnSurfaceMuted)
                ListItem(
                    headlineContent = { Text("Copy MAC Address (for Airgeddon)") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MAC Address", host.mac ?: ""))
                        selectedHost = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Copy IP Address") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("IP Address", host.ipv4 ?: ""))
                        selectedHost = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Bettercap Options
                Text("Bettercap Options", style = MaterialTheme.typography.labelMedium, color = OnSurfaceMuted)
                ListItem(
                    headlineContent = { Text("Port Scan — common (1–1024)") },
                    supportingContent = { Text("Results stream to Events tab", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Radar, null) },
                    modifier = Modifier.clickable {
                        host.ipv4?.let { onStartScan("syn.scan $it/32 1 1024") }
                        selectedHost = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Port Scan — full (1–65535)") },
                    supportingContent = { Text("Results stream to Events tab", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Radar, null) },
                    modifier = Modifier.clickable {
                        host.ipv4?.let { onStartScan("syn.scan $it/32 1 65535") }
                        selectedHost = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Ban Device (arp.ban)") },
                    leadingContent = { Icon(Icons.Default.Block, null, tint = ErrorRed) },
                    modifier = Modifier.clickable {
                        host.ipv4?.let { onExecuteCommand("set arp.spoof.targets $it; arp.ban on") }
                        selectedHost = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    if (uiState.showInterfaceDialog && session != null) {
        val nonLoopbackIfaces = session.interfaces?.filter { !it.isLoopback } ?: emptyList()
        AlertDialog(
            onDismissRequest = onDismissInterfaceDialog,
            title = { Text("Select Network Interface") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(nonLoopbackIfaces) { iface ->
                        Card(
                            onClick = { iface.name?.let { onSelectInterface(it) } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    iface.name ?: "Unknown",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GreenPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "IP: ${iface.resolvedIpv4 ?: "—"} · MAC: ${iface.mac ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceMuted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissInterfaceDialog) {
                    Text("Dismiss", color = OnSurfaceMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun DashCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GreenPrimary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted, modifier = Modifier.width(88.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ModuleChip(module: BcModule) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .padding(end = 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(color = GreenPrimary, shape = MaterialTheme.shapes.small) {
                Spacer(Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            module.name ?: "unknown",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun guessDeviceIcon(host: Host): androidx.compose.ui.graphics.vector.ImageVector {
    val vendor = host.vendor?.lowercase() ?: ""
    val hostname = host.hostname?.lowercase() ?: ""
    return when {
        vendor.containsAny("cisco", "netgear", "tp-link", "ubiquiti", "mikrotik", "d-link",
            "asus", "linksys", "aruba", "ruckus", "zyxel", "juniper", "extreme") ||
        hostname.containsAny("router", "gateway", "gw-", "-gw", "wap", "ap-", "-ap", "access-point") ->
            Icons.Default.Router

        vendor.containsAny("apple", "samsung", "oneplus", "xiaomi", "huawei", "lg electronics",
            "motorola", "google", "sony mobile", "oppo", "vivo", "realme", "nothing") ||
        hostname.containsAny("iphone", "ipad", "android", "pixel", "galaxy", "phone") ->
            Icons.Default.PhoneAndroid

        vendor.containsAny("intel", "dell", "hewlett", "lenovo", "acer", "microsoft",
            "gigabyte", "asrock", "msi", "supermicro", "vmware", "realtek") ||
        hostname.containsAny("macbook", "imac", "desktop", "laptop", "workstation", "server",
            "ubuntu", "debian", "fedora", "arch", "windows", "win-", "pc-") ->
            Icons.Default.Computer

        else -> Icons.Default.Devices
    }
}

private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it, ignoreCase = true) }

@Composable
private fun HostCard(host: Host, onClick: () -> Unit) {
    val identified = !host.hostname.isNullOrBlank() || !host.vendor.isNullOrBlank()
    val icon = guessDeviceIcon(host)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (identified) CyanAccent else OnSurfaceMuted,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    host.ipv4 ?: host.ipv6 ?: "Unknown IP",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!host.hostname.isNullOrBlank()) {
                    Text(host.hostname, style = MaterialTheme.typography.bodyMedium, color = GreenPrimary)
                }
                Text(
                    host.mac ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMuted
                )
                if (!host.vendor.isNullOrBlank()) {
                    Text(
                        host.vendor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
