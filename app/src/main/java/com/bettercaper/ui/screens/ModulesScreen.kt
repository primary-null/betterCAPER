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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettercaper.api.model.BcModule
import com.bettercaper.ui.components.ScreenTitle
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState
import com.google.gson.Gson

private val gson = Gson()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    uiState: AppUiState,
    onToggleModule: (name: String, turningOn: Boolean) -> Unit,
    onRunCommand: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val modules = (uiState.session?.modules ?: emptyList())
        .sortedWith(compareByDescending { it.running == true })
    val runningCount = modules.count { it.running == true }
    val arpModule = modules.firstOrNull { it.name == "arp.spoof" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ScreenTitle(
                        "Modules",
                        uiState,
                        subtitle = "$runningCount running · ${modules.size} total"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        if (modules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No modules available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMuted
                )
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
                if (arpModule != null) {
                    item {
                        ArpSpoofCard(
                            module = arpModule,
                            onToggle = { turningOn -> onToggleModule("arp.spoof", turningOn) },
                            onRunCommand = onRunCommand
                        )
                    }
                }

                items(
                    modules.filter { it.name != "arp.spoof" },
                    key = { it.name ?: modules.indexOf(it).toString() }
                ) { module ->
                    ModuleCard(module, onToggleModule, onRunCommand)
                }
            }
        }
    }
}

@Composable
private fun ArpSpoofCard(
    module: BcModule,
    onToggle: (Boolean) -> Unit,
    onRunCommand: (String) -> Unit
) {
    val running = module.running == true
    val params = module.parameters ?: emptyMap()

    var targets by remember(params) {
        mutableStateOf(params["arp.spoof.targets"]?.toString()?.trim('"') ?: "")
    }
    var fullDuplex by remember(params) {
        mutableStateOf(params["arp.spoof.fullduplex"]?.toString() == "true")
    }
    var internal by remember(params) {
        mutableStateOf(params["arp.spoof.internal"]?.toString() == "true")
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (running) SurfaceVariant else SurfaceDark
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = if (running) WarningAmber else OnSurfaceMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ARP Spoof",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (running) WarningAmber else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (running) "Active — intercepting traffic" else "Inactive",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (running) WarningAmber else OnSurfaceMuted
                    )
                }
                Switch(
                    checked = running,
                    onCheckedChange = { newValue -> onToggle(newValue) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BackgroundDark,
                        checkedTrackColor = WarningAmber
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Text(
                "Targets",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceMuted
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = targets,
                    onValueChange = { targets = it },
                    placeholder = {
                        Text("192.168.1.x, 192.168.1.y", color = OnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onRunCommand("set arp.spoof.targets $targets") },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Set", color = BackgroundDark)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = fullDuplex,
                        onCheckedChange = {
                            fullDuplex = it
                            onRunCommand("set arp.spoof.fullduplex $it")
                        },
                        colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
                    )
                    Text(
                        "Full duplex",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = internal,
                        onCheckedChange = {
                            internal = it
                            onRunCommand("set arp.spoof.internal $it")
                        },
                        colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
                    )
                    Text(
                        "Internal",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: BcModule,
    onToggle: (name: String, turningOn: Boolean) -> Unit,
    onRunCommand: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val running = module.running == true
    val params = module.parameters?.entries?.toList() ?: emptyList()

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = params.isNotEmpty()) { expanded = !expanded }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (running) GreenPrimary else OnSurfaceMuted,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        module.name ?: "unknown",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (running) GreenPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    if (!module.description.isNullOrBlank()) {
                        Text(
                            module.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = running,
                    onCheckedChange = { newValue -> module.name?.let { onToggle(it, newValue) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BackgroundDark,
                        checkedTrackColor = GreenPrimary
                    )
                )
            }

            if (params.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (expanded) "hide params ▲" else "show params ▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = SurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    params.forEach { (key, value) ->
                        EditableParamRow(key, gson.toJson(value).trim('"'), onRunCommand)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableParamRow(key: String, initialValue: String, onRunCommand: (String) -> Unit) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val isBoolean = initialValue == "true" || initialValue == "false"

    if (isBoolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = value == "true",
                onCheckedChange = {
                    value = it.toString()
                    onRunCommand("set $key $value")
                },
                colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
            )
            Text(
                key,
                style = MaterialTheme.typography.bodySmall,
                color = CyanAccent
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                key,
                style = MaterialTheme.typography.bodySmall,
                color = CyanAccent,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onRunCommand("set $key $value") },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Set", color = BackgroundDark, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
