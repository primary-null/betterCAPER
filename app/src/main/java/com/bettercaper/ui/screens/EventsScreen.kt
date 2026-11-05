package com.bettercaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettercaper.api.model.BcEvent
import com.bettercaper.ui.theme.*
import com.bettercaper.viewmodel.AppUiState
import com.google.gson.Gson
import com.google.gson.GsonBuilder

private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    uiState: AppUiState,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    var filterQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val filteredEvents = remember(uiState.events, filterQuery) {
        if (filterQuery.isBlank()) uiState.events
        else uiState.events.filter { 
            it.tag?.contains(filterQuery, ignoreCase = true) == true || 
            it.data?.toString()?.contains(filterQuery, ignoreCase = true) == true 
        }
    }

    LaunchedEffect(filteredEvents.size) {
        if (filteredEvents.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        TextField(
                            value = filterQuery,
                            onValueChange = { filterQuery = it },
                            placeholder = { Text("Filter events...", color = OnSurfaceMuted) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = GreenPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Event Stream", color = GreenPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSearchExpanded) {
                        IconButton(onClick = { 
                            filterQuery = ""
                            isSearchExpanded = false 
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    Badge(
                        containerColor = GreenPrimary,
                        contentColor = BackgroundDark
                    ) {
                        Text("${filteredEvents.size}")
                    }
                    Spacer(Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        if (filteredEvents.isEmpty() && uiState.events.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No events match your filter.", color = OnSurfaceMuted)
            }
        } else if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GreenPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Waiting for events...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(filteredEvents, key = { "${it.time}_${it.tag}" }) { event ->
                    EventItem(event)
                }
            }
        }
    }
}

@Composable
private fun EventItem(event: BcEvent) {
    val tag = event.tag ?: "unknown"
    val tagColor = tagColor(tag)

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = tagColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = tagColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    event.time?.take(19)?.replace("T", " ") ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted
                )
            }
            if (event.data != null) {
                Spacer(Modifier.height(6.dp))
                val dataStr = runCatching {
                    gson.toJson(event.data)
                }.getOrElse { event.data.toString() }
                Text(
                    dataStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun tagColor(tag: String): Color = when {
    tag.startsWith("wifi") -> TagBlue
    tag.startsWith("ble") || tag.startsWith("bt") -> TagPurple
    tag.startsWith("net") -> TagGreen
    tag.startsWith("syn") || tag.startsWith("http") -> TagOrange
    else -> OnSurfaceMuted
}
