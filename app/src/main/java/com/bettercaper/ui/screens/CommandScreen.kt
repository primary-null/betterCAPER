package com.bettercaper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bettercaper.ui.components.ScreenTitle
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState

private data class CmdSuggestion(val cmd: String, val description: String)
private data class CmdGroup(val title: String, val color: androidx.compose.ui.graphics.Color, val cmds: List<CmdSuggestion>)

private fun buildSuggestions(uiState: AppUiState): List<CmdGroup> {
    val iface = uiState.session?.iface?.name
    val gateway = uiState.session?.gateway?.ipv4
    val ifaceLabel = iface ?: "<iface>"
    val gwLabel = gateway ?: "<gateway-ip>"

    return listOf(
        CmdGroup("Network", GreenPrimary, listOf(
            CmdSuggestion("net.probe on", "Discover hosts by probing the subnet"),
            CmdSuggestion("net.probe off", "Stop probing"),
            CmdSuggestion("net.recon on", "Start passive host discovery"),
            CmdSuggestion("net.recon off", "Stop passive discovery"),
            CmdSuggestion("net.show", "Show discovered hosts"),
            CmdSuggestion("set net.iface $ifaceLabel", "Set active network interface"),
        )),
        CmdGroup("ARP Spoof", WarningAmber, listOf(
            CmdSuggestion("set arp.spoof.targets $gwLabel", "Target the gateway"),
            CmdSuggestion("set arp.spoof.fullduplex true", "Intercept both directions"),
            CmdSuggestion("set arp.spoof.internal true", "Also spoof LAN ↔ LAN"),
            CmdSuggestion("arp.spoof on", "Start ARP spoofing"),
            CmdSuggestion("arp.spoof off", "Stop ARP spoofing"),
            CmdSuggestion("arp.ban on", "Cut off target from network"),
        )),
        CmdGroup("WiFi", TagBlue, listOf(
            CmdSuggestion("wifi.recon on", "Start WiFi AP/client scanning"),
            CmdSuggestion("wifi.recon off", "Stop scanning"),
            CmdSuggestion("wifi.show", "Show access points"),
            CmdSuggestion("set wifi.interface wlan0", "Set WiFi interface (wireless adapter)"),
            CmdSuggestion("wifi.deauth <BSSID>", "Deauth one AP. Run wifi.recon on and select that AP first"),
        )),
        CmdGroup("BLE", TagPurple, listOf(
            CmdSuggestion("ble.recon on", "Start BLE device scanning"),
            CmdSuggestion("ble.recon off", "Stop scanning"),
            CmdSuggestion("ble.show", "Show BLE devices"),
        )),
        CmdGroup("Sniffing", TagOrange, listOf(
            CmdSuggestion("net.sniff on", "Start packet sniffer"),
            CmdSuggestion("net.sniff off", "Stop sniffer"),
            CmdSuggestion("set net.sniff.verbose true", "Show full packet details"),
            CmdSuggestion("set net.sniff.filter tcp port 80", "Filter HTTP traffic"),
        )),
        CmdGroup("General", OnSurfaceMuted, listOf(
            CmdSuggestion("help", "Show all commands"),
            CmdSuggestion("active", "Show running modules"),
            CmdSuggestion("set", "Show all variables"),
            CmdSuggestion("events.clear", "Clear event log"),
        )),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandScreen(
    uiState: AppUiState,
    onSendCommand: (String) -> Unit,
    onClearOutput: () -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var outputExpanded by remember { mutableStateOf(true) }
    val history = uiState.commandHistory
    val groups = remember(uiState.session) { buildSuggestions(uiState) }

    // Auto-expand when a command response arrives
    LaunchedEffect(uiState.commandOutput) {
        if (uiState.commandOutput != null) outputExpanded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Command", uiState) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            Surface(color = SurfaceDark, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isCliCommand = input.trimStart().startsWith("bettercap")
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("bettercap command...", color = OnSurfaceMuted) },
                        singleLine = true,
                        isError = isCliCommand,
                        supportingText = if (isCliCommand) {
                            { Text("Use bettercap commands only — e.g. set net.iface eth0", color = ErrorRed, style = MaterialTheme.typography.labelSmall) }
                        } else null,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            cursorColor = GreenPrimary,
                            errorBorderColor = ErrorRed
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val cmd = input.trim()
                            if (cmd.isNotBlank()) {
                                onSendCommand(cmd)
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank() && !uiState.commandLoading
                    ) {
                        if (uiState.commandLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GreenPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = GreenPrimary)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (outputExpanded) Modifier.weight(1f) else Modifier)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp).then(if (outputExpanded) Modifier.fillMaxHeight() else Modifier)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OUTPUT", style = MaterialTheme.typography.labelSmall, color = GreenPrimary)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { outputExpanded = !outputExpanded },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                if (outputExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (outputExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = OnSurfaceMuted
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onClearOutput, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp), tint = OnSurfaceMuted)
                        }
                    }
                    if (outputExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(BackgroundDark, MaterialTheme.shapes.small)
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                uiState.commandOutput ?: "Ready...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = GreenPrimary
                            )
                        }
                    }
                }
            }

            // Suggestions drawer
            Card(
                colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!outputExpanded) Modifier.weight(1f) else Modifier.heightIn(max = 200.dp))
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (history.isNotEmpty()) {
                        item {
                            Text("History", style = MaterialTheme.typography.titleSmall,
                                color = CyanAccent, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(history) { cmd ->
                            SuggestionRow(cmd, null, CyanAccent) { input = cmd }
                        }
                        item { HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(vertical = 6.dp)) }
                    }

                    groups.forEach { group ->
                        item {
                            Text(group.title, style = MaterialTheme.typography.titleSmall,
                                color = group.color, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        }
                        items(group.cmds) { suggestion ->
                            SuggestionRow(suggestion.cmd, suggestion.description, group.color) { input = suggestion.cmd }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    cmd: String,
    description: String?,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(">", style = MaterialTheme.typography.bodyMedium, color = GreenPrimary,
            modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(cmd, style = MaterialTheme.typography.bodyMedium, color = color)
            if (!description.isNullOrBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
            }
        }
    }
}
