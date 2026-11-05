package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bettercaper.viewmodel.PcapUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcapScreen(
    state: PcapUiState,
    onLoadPcaps: () -> Unit,
    onDownloadPcap: (String) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (state.pcaps.isEmpty() && !state.loading) {
            onLoadPcaps()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PCAP Exports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onLoadPcaps) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.loading && state.pcaps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null && state.pcaps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Error loading PCAPs: ${state.error}", color = MaterialTheme.colorScheme.error)
                }
            } else if (state.pcaps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No PCAP files found. Try capturing traffic with net.sniff.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.pcaps) { pcap ->
                        val name = pcap.substringAfterLast("/")
                        val isDownloading = state.downloadingFile == pcap
                        val isDownloaded = state.downloadedFiles.any { it.endsWith(name) }

                        ListItem(
                            headlineContent = { Text(name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { 
                                Text(
                                    if (isDownloaded) "Downloaded to device" else pcap,
                                    color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                if (isDownloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else if (!isDownloaded) {
                                    IconButton(onClick = { onDownloadPcap(pcap) }) {
                                        Icon(Icons.Default.Download, "Download PCAP", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
