package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState
import com.bettercaper.viewmodel.ConnectParams
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun ConnectScreen(
    uiState: AppUiState,
    hasSavedConnections: Boolean = false,
    onConnect: (ConnectParams) -> Unit,
    onClearError: () -> Unit,
    onClearFingerprint: (host: String, port: Int) -> Unit,
    onConnected: () -> Unit,
    onNavigationConsumed: () -> Unit = {},
    onNavigateSaved: () -> Unit = {}
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Navigate to Dashboard only when the ViewModel sets the one-shot flag after a
    // successful connection. The flag is cleared immediately after we consume it, so
    // returning via Back while still connected does NOT retrigger the navigate.
    LaunchedEffect(uiState.navigateToDashboard) {
        if (uiState.navigateToDashboard) {
            onNavigationConsumed()  // clear the flag in ViewModel first
            onConnected()
        }
    }

    var sshHost by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUser by remember { mutableStateOf("") }
    var sshPass by remember { mutableStateOf("") }
    var sshKey by remember { mutableStateOf("") }
    var useSshKey by remember { mutableStateOf(false) }
    var apiPort by remember { mutableStateOf("8081") }
    var apiUser by remember { mutableStateOf("user") }
    var apiPass by remember { mutableStateOf("user") }
    var sshPassVisible by remember { mutableStateOf(false) }
    var apiPassVisible by remember { mutableStateOf(false) }

    val isFingerprintError = uiState.error?.startsWith("Host key changed") == true

    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text(if (isFingerprintError) "Host Key Changed" else "Connection Error") },
            text = { Text(uiState.error) },
            confirmButton = {
                TextButton(onClick = onClearError) { Text("OK") }
            },
            dismissButton = if (isFingerprintError) {
                {
                    TextButton(onClick = {
                        onClearFingerprint(sshHost.trim(), sshPort.toIntOrNull() ?: 22)
                        onClearError()
                    }) { Text("Trust New Key", color = androidx.compose.ui.graphics.Color(0xFFFFB300)) }
                }
            } else null
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.reconnectingMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
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

        Spacer(Modifier.height(32.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "betterCAPER",
                style = MaterialTheme.typography.headlineLarge,
                color = GreenPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = onNavigateSaved,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    if (hasSavedConnections) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = "Saved connections",
                    tint = GreenPrimary
                )
            }
        }
        Text(
            text = "bettercap remote interface",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMuted
        )

        Spacer(Modifier.height(40.dp))

        SectionLabel("SSH Connection")

        OutlinedTextField(
            value = sshHost,
            onValueChange = { sshHost = it },
            label = { Text("Host / IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = sshPort,
                onValueChange = { sshPort = it },
                label = { Text("SSH Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = sshUser,
                onValueChange = { sshUser = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
        }
        Spacer(Modifier.height(8.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use SSH Key", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = useSshKey, onCheckedChange = { useSshKey = it })
        }
        
        if (useSshKey) {
            OutlinedTextField(
                value = sshKey,
                onValueChange = { sshKey = it },
                label = { Text("Private Key (PEM/OpenSSH)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
            )
        } else {
            OutlinedTextField(
                value = sshPass,
                onValueChange = { sshPass = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (sshPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { sshPassVisible = !sshPassVisible }) {
                        Icon(
                            imageVector = if (sshPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Bettercap API")

        OutlinedTextField(
            value = apiPort,
            onValueChange = { apiPort = it },
            label = { Text("API Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = apiUser,
                onValueChange = { apiUser = it },
                label = { Text("API User") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = apiPass,
                onValueChange = { apiPass = it },
                label = { Text("API Password") },
                singleLine = true,
                visualTransformation = if (apiPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiPassVisible = !apiPassVisible }) {
                        Icon(
                            imageVector = if (apiPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                onConnect(
                    ConnectParams(
                        sshHost = sshHost.trim(),
                        sshPort = sshPort.toIntOrNull() ?: 22,
                        sshUsername = sshUser.trim(),
                        sshPassword = if (!useSshKey) sshPass else "",
                        sshKey = if (useSshKey) sshKey.trim() else null,
                        remoteApiPort = apiPort.toIntOrNull() ?: 8081,
                        apiUsername = apiUser.trim(),
                        apiPassword = apiPass
                    )
                )
            },
            enabled = sshHost.isNotBlank() && sshUser.isNotBlank() && !uiState.connecting,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (uiState.connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Connecting...")
            } else {
                Text("Connect")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = OnSurfaceMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}
