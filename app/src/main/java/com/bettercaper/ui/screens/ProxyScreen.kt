package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    uiState: AppUiState,
    onToggleModule: (name: String, turningOn: Boolean) -> Unit,
    onRunCommand: (String) -> Unit,
    onBack: () -> Unit
) {
    val modules = uiState.session?.modules ?: emptyList()
    val httpModule = modules.firstOrNull { it.name == "http.proxy" }
    val httpsModule = modules.firstOrNull { it.name == "https.proxy" }
    val tcpModule = modules.firstOrNull { it.name == "tcp.proxy" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxies", color = GreenPrimary) },
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

            // HTTP Proxy
            if (httpModule != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Http, contentDescription = null, tint = TagBlue)
                            Spacer(Modifier.width(12.dp))
                            Text("HTTP Proxy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = httpModule.running == true,
                                onCheckedChange = { onToggleModule("http.proxy", it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenPrimary.copy(alpha = 0.3f))
                            )
                        }
                        
                        var port by remember(httpModule) { mutableStateOf((httpModule.parameters?.get("http.port") as? Map<String, Any>)?.get("value")?.toString() ?: "80") }
                        var script by remember(httpModule) { mutableStateOf((httpModule.parameters?.get("http.proxy.script") as? Map<String, Any>)?.get("value")?.toString() ?: "") }
                        
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("HTTP Port") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = script,
                            onValueChange = { script = it },
                            label = { Text("Proxy Script Path (.js)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onRunCommand("set http.port $port")
                                if (script.isNotBlank()) onRunCommand("set http.proxy.script $script")
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Apply settings")
                        }
                    }
                }
            }

            // HTTPS Proxy
            if (httpsModule != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GreenPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("HTTPS Proxy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = httpsModule.running == true,
                                onCheckedChange = { onToggleModule("https.proxy", it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenPrimary.copy(alpha = 0.3f))
                            )
                        }
                        
                        var port by remember(httpsModule) { mutableStateOf((httpsModule.parameters?.get("https.port") as? Map<String, Any>)?.get("value")?.toString() ?: "443") }
                        var script by remember(httpsModule) { mutableStateOf((httpsModule.parameters?.get("https.proxy.script") as? Map<String, Any>)?.get("value")?.toString() ?: "") }
                        var certificate by remember(httpsModule) { mutableStateOf((httpsModule.parameters?.get("https.proxy.certificate") as? Map<String, Any>)?.get("value")?.toString() ?: "") }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("HTTPS Port") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = script,
                            onValueChange = { script = it },
                            label = { Text("Proxy Script Path (.js)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = certificate,
                            onValueChange = { certificate = it },
                            label = { Text("Custom Certificate Path (.pem)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onRunCommand("set https.port $port")
                                if (script.isNotBlank()) onRunCommand("set https.proxy.script $script")
                                if (certificate.isNotBlank()) onRunCommand("set https.proxy.certificate $certificate")
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Apply settings")
                        }
                    }
                }
            }
            
            // TCP Proxy
            if (tcpModule != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = WarningAmber)
                            Spacer(Modifier.width(12.dp))
                            Text("TCP Proxy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = tcpModule.running == true,
                                onCheckedChange = { onToggleModule("tcp.proxy", it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenPrimary.copy(alpha = 0.3f))
                            )
                        }
                        
                        var port by remember(tcpModule) { mutableStateOf((tcpModule.parameters?.get("tcp.port") as? Map<String, Any>)?.get("value")?.toString() ?: "8080") }
                        var address by remember(tcpModule) { mutableStateOf((tcpModule.parameters?.get("tcp.proxy.address") as? Map<String, Any>)?.get("value")?.toString() ?: "") }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("TCP Port (Local)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Target Address (IP:Port)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onRunCommand("set tcp.port $port")
                                onRunCommand("set tcp.proxy.address $address")
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Apply settings")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
