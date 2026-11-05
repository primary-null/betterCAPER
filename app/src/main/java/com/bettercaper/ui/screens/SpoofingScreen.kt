package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofingScreen(
    uiState: AppUiState,
    onToggleModule: (name: String, turningOn: Boolean) -> Unit,
    onRunCommand: (String) -> Unit,
    onBack: () -> Unit
) {
    val modules = uiState.session?.modules ?: emptyList()
    val arpModule = modules.firstOrNull { it.name == "arp.spoof" }
    val dnsModule = modules.firstOrNull { it.name == "dns.spoof" }
    val dhcp6Module = modules.firstOrNull { it.name == "dhcp6.spoof" }
    val macModule = modules.firstOrNull { it.name == "mac.changer" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Spoofing", color = GreenPrimary) },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ARP Spoof
            if (arpModule != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = WarningAmber)
                            Spacer(Modifier.width(12.dp))
                            Text("ARP Spoofing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = arpModule.running == true,
                                onCheckedChange = { onToggleModule("arp.spoof", it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenPrimary.copy(alpha = 0.3f))
                            )
                        }
                        
                        var targets by remember(arpModule) { mutableStateOf((arpModule.parameters?.get("arp.spoof.targets") as? Map<String, Any>)?.get("value")?.toString() ?: "") }
                        var internal by remember(arpModule) { mutableStateOf((arpModule.parameters?.get("arp.spoof.internal") as? Map<String, Any>)?.get("value")?.toString() == "true") }
                        var fullduplex by remember(arpModule) { mutableStateOf((arpModule.parameters?.get("arp.spoof.fullduplex") as? Map<String, Any>)?.get("value")?.toString() == "true") }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = targets,
                            onValueChange = { targets = it },
                            label = { Text("Targets (Comma separated IPs)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Internal (Spoof local connections)", modifier = Modifier.weight(1f))
                            Checkbox(checked = internal, onCheckedChange = { internal = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Full Duplex (Spoof both target and gateway)", modifier = Modifier.weight(1f))
                            Checkbox(checked = fullduplex, onCheckedChange = { fullduplex = it })
                        }
                        Button(
                            onClick = {
                                onRunCommand("set arp.spoof.targets $targets")
                                onRunCommand("set arp.spoof.internal $internal")
                                onRunCommand("set arp.spoof.fullduplex $fullduplex")
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Apply settings")
                        }
                    }
                }
            }

            // DNS Spoof
            if (dnsModule != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = CyanAccent)
                            Spacer(Modifier.width(12.dp))
                            Text("DNS Spoofing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = dnsModule.running == true,
                                onCheckedChange = { onToggleModule("dns.spoof", it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenPrimary.copy(alpha = 0.3f))
                            )
                        }
                        
                        var hostsFile by remember(dnsModule) { mutableStateOf((dnsModule.parameters?.get("dns.spoof.hosts") as? Map<String, Any>)?.get("value")?.toString() ?: "") }
                        var address by remember(dnsModule) { mutableStateOf((dnsModule.parameters?.get("dns.spoof.address") as? Map<String, Any>)?.get("value")?.toString() ?: "") }
                        var domains by remember(dnsModule) { mutableStateOf((dnsModule.parameters?.get("dns.spoof.domains") as? Map<String, Any>)?.get("value")?.toString() ?: "*") }
                        var all by remember(dnsModule) { mutableStateOf((dnsModule.parameters?.get("dns.spoof.all") as? Map<String, Any>)?.get("value")?.toString() == "true") }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = domains,
                            onValueChange = { domains = it },
                            label = { Text("Domains to spoof (comma separated)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Target IP Address (Leave empty to use local IP)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Spoof ALL DNS queries", modifier = Modifier.weight(1f))
                            Checkbox(checked = all, onCheckedChange = { all = it })
                        }
                        Button(
                            onClick = {
                                onRunCommand("set dns.spoof.domains $domains")
                                if (address.isNotBlank()) onRunCommand("set dns.spoof.address $address")
                                onRunCommand("set dns.spoof.all $all")
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Apply settings")
                        }
                    }
                }
            }
            
            // MAC Changer
            if (macModule != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NetworkWifi, contentDescription = null, tint = TagBlue)
                            Spacer(Modifier.width(12.dp))
                            Text("MAC Changer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        
                        var iface by remember(macModule) { mutableStateOf((macModule.parameters?.get("mac.changer.iface") as? Map<String, Any>)?.get("value")?.toString() ?: "") }
                        var address by remember(macModule) { mutableStateOf((macModule.parameters?.get("mac.changer.address") as? Map<String, Any>)?.get("value")?.toString() ?: "random") }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = iface,
                            onValueChange = { iface = it },
                            label = { Text("Interface (e.g. wlan0)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("New MAC (or 'random')") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    onRunCommand("set mac.changer.iface $iface")
                                    onRunCommand("set mac.changer.address $address")
                                    onRunCommand("mac.changer on")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Change MAC")
                            }
                            OutlinedButton(
                                onClick = {
                                    onRunCommand("mac.changer off")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Restore Default")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
