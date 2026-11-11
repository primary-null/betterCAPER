package com.bettercaper.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.bettercaper.ui.theme.GreenPrimary
import com.bettercaper.ui.theme.OnSurfaceMuted
import com.bettercaper.ui.theme.WarningAmber
import com.bettercaper.viewmodel.AppUiState

@Composable
fun ScreenTitle(
    title: String,
    uiState: AppUiState,
    subtitle: String? = null
) {
    Column {
        Text(title, color = GreenPrimary, style = MaterialTheme.typography.titleLarge)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SessionStatusLine(uiState)
    }
}

@Composable
fun SessionStatusLine(uiState: AppUiState, modifier: Modifier = Modifier) {
    val iface = uiState.session?.iface
    val ifaceLabel = buildString {
        val name = iface?.name?.takeIf { it.isNotBlank() && it != "null" }
        val ip = iface?.resolvedIpv4?.takeIf { it.isNotBlank() }
        if (name != null) append(name)
        if (ip != null) {
            if (isNotEmpty()) append(" · ")
            append(ip)
        }
    }.takeIf { it.isNotEmpty() }
    val endpoint = uiState.endpointLabel?.takeIf { it.isNotBlank() }
    val summary = listOfNotNull(endpoint, ifaceLabel).joinToString("  ·  ")
    val reconnecting = uiState.reconnectingMessage
    if (summary.isEmpty() && reconnecting == null) return
    Column(modifier) {
        if (summary.isNotEmpty()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (reconnecting != null) {
            Text(
                reconnecting,
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
