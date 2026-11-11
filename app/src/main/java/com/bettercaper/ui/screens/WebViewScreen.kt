package com.bettercaper.ui.screens

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.bettercaper.ui.components.ScreenTitle
import com.bettercaper.ui.theme.SurfaceDark
import com.bettercaper.ui.theme.WarningAmber
import com.bettercaper.viewmodel.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    uiState: AppUiState,
    onBack: () -> Unit
) {
    val port = uiState.localUiPort
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Bettercap Web UI", uiState) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (port == null || port == -1) {
                Text(
                    "Web UI port forwarder not initialized.",
                    color = WarningAmber,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (errorMsg != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Error loading Web UI.",
                        color = WarningAmber
                    )
                    Text(
                        errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { webView?.reload(); errorMsg = null }) {
                        Text("Retry")
                    }
                    Text(
                        "Make sure the 'ui' caplet is running in Bettercap.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        errorMsg = error?.description?.toString() ?: "Unknown error"
                                    }
                                }
                            }
                            loadUrl("http://127.0.0.1:$port/")
                            webView = this
                        }
                    },
                    update = {
                        webView = it
                    },
                    onRelease = { view ->
                        view.stopLoading()
                        view.destroy()
                        webView = null
                    }
                )
            }
        }
    }
}
