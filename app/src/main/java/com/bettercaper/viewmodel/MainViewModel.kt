package com.bettercaper.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.bettercaper.api.BettercapApiClient
import com.bettercaper.api.model.BcEvent
import com.bettercaper.api.model.SessionResponse
import com.bettercaper.location.LocationTracker
import com.bettercaper.data.AirgeddonEvent
import com.bettercaper.data.AIRGEDDON_LOG_PATH
import com.bettercaper.data.AIRGEDDON_PLUGIN_PATH
import com.bettercaper.data.ConnectionRepository
import com.bettercaper.data.SavedConnection
import com.bettercaper.data.SecureStorage
import com.bettercaper.data.parseAirgeddonEvents
import com.bettercaper.data.WIFI_BLE_CAPLET_PATH
import com.bettercaper.data.wifiBleCapletBase64
import com.bettercaper.data.pluginBase64
import com.bettercaper.ssh.OpenedTunnel
import com.bettercaper.ssh.ReconnectAction
import com.bettercaper.ssh.ReconnectPolicy
import com.bettercaper.ssh.SshTunnelManager
import com.google.gson.Gson
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

data class ConnectParams(
    val sshHost: String,
    val sshPort: Int = 22,
    val sshUsername: String,
    val sshPassword: String = "",
    val sshKey: String? = null,
    val remoteApiPort: Int = 8081,
    val apiUsername: String = "user",
    val apiPassword: String = "user",
    val defaultIface: String? = null,
    val savedConnectionId: String? = null
)

data class AppUiState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val reconnectingMessage: String? = null,
    val session: SessionResponse? = null,
    val events: List<BcEvent> = emptyList(),
    val commandOutput: String? = null,
    val commandLoading: Boolean = false,
    val showInterfaceDialog: Boolean = false,
    val savedConnectionId: String? = null,
    val localUiPort: Int? = null,
    val endpointLabel: String? = null,
    // One-shot flag: set true when connection succeeds, cleared immediately after navigation.
    // Prevents ConnectScreen re-triggering navigation when returning via Back while still connected.
    val navigateToDashboard: Boolean = false,
    val commandHistory: List<String> = emptyList()
)

data class AirgeddonUiState(
    val pluginDeployed: Boolean = false,
    val events: List<AirgeddonEvent> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class WifiBleUiState(
    val capletDeployed: Boolean = false,
    val scanning: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

data class CapletsUiState(
    val loading: Boolean = false,
    val caplets: List<String> = emptyList(),
    val error: String? = null,
    val editingCaplet: String? = null,
    val editingContent: String? = null
)

data class PcapUiState(
    val loading: Boolean = false,
    val pcaps: List<String> = emptyList(),
    val downloadingFile: String? = null,
    val downloadedFiles: List<String> = emptyList(),
    val error: String? = null
)


@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val secureStorage: SecureStorage,
    private val repo: ConnectionRepository,
    private val tunnelManager: SshTunnelManager,
    private val locationTracker: LocationTracker
) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _toastEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _savedConnections = MutableStateFlow<List<SavedConnection>>(emptyList())
    val savedConnections: StateFlow<List<SavedConnection>> = _savedConnections.asStateFlow()

    fun showToast(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(message)
        }
    }

    private val _airgeddonState = MutableStateFlow(AirgeddonUiState())
    val airgeddonState: StateFlow<AirgeddonUiState> = _airgeddonState.asStateFlow()

    private val _wifiBleState = MutableStateFlow(WifiBleUiState())
    val wifiBleState: StateFlow<WifiBleUiState> = _wifiBleState.asStateFlow()

    private val _capletsState = MutableStateFlow(CapletsUiState())
    val capletsState: StateFlow<CapletsUiState> = _capletsState.asStateFlow()

    private val _pcapState = MutableStateFlow(PcapUiState())
    val pcapState: StateFlow<PcapUiState> = _pcapState.asStateFlow()


    private var apiClient: BettercapApiClient? = null
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    
    private var activeParams: ConnectParams? = null
    private var isIntentionalDisconnect = false
    private val reconnectPolicy = ReconnectPolicy()
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null

    init {
        _savedConnections.value = repo.load()
        if (secureStorage.keystoreUnavailable) {
            showToast("Saved secrets are not encrypted on this device because the Android keystore is unavailable.")
        }
    }

    fun connect(params: ConnectParams) {
        activeParams = params
        isIntentionalDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        val generation = reconnectPolicy.beginUserAttempt()
        launchConnection(params, generation, retryOnFailure = false)
    }

    /**
     * One connection attempt. The previous socket is closed first, and its callbacks
     * carry an older generation so they cannot start a second handshake.
     * [tunnelManager.disconnect] runs while the attempt is still open so a disconnect
     * callback cannot schedule a reconnect of its own.
     */
    private fun launchConnection(params: ConnectParams, generation: Int, retryOnFailure: Boolean) {
        connectionJob?.cancel()
        val previousSocket = webSocket
        webSocket = null
        connectionJob = viewModelScope.launch {
            previousSocket?.close(1000, null)
            _uiState.update {
                it.copy(connecting = true, error = null, endpointLabel = params.sshHost)
            }
            var opened: OpenedTunnel? = null
            try {
                val tunnel = withContext(Dispatchers.IO) {
                    tunnelManager.disconnect()
                    tunnelManager.connect(
                        host = params.sshHost,
                        sshPort = params.sshPort,
                        username = params.sshUsername,
                        password = params.sshPassword,
                        privateKey = params.sshKey,
                        remoteApiPort = params.remoteApiPort,
                        onDisconnect = { onTransportLost(generation) }
                    )
                }
                opened = tunnel
                if (!reconnectPolicy.isCurrent(generation)) {
                    withContext(Dispatchers.IO) { runCatching { tunnelManager.releaseEpoch(tunnel.epoch) } }
                    return@launch
                }
                val client = BettercapApiClient(tunnel.localPort, params.apiUsername, params.apiPassword)
                apiClient = client

                val hasLocationPermission = ContextCompat.checkSelfPermission(
                    getApplication(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasLocationPermission) locationTracker.startTracking()

                val session = withContext(Dispatchers.IO) { client.getSession() }
                if (!reconnectPolicy.isCurrent(generation)) {
                    withContext(Dispatchers.IO) { runCatching { tunnelManager.releaseEpoch(tunnel.epoch) } }
                    return@launch
                }

                if (!params.defaultIface.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        runCatching { client.runCommand("set net.iface ${params.defaultIface}") }
                    }
                }

                val hasNonLoopback = (session.interfaces?.filter { !it.isLoopback }?.size ?: 0) > 0
                val showDialog = hasNonLoopback && params.defaultIface.isNullOrBlank()
                val initialEvents = withContext(Dispatchers.IO) {
                    try { client.getEvents(100).reversed() } catch (_: Exception) { emptyList() }
                }
                if (!reconnectPolicy.isCurrent(generation)) {
                    withContext(Dispatchers.IO) { runCatching { tunnelManager.releaseEpoch(tunnel.epoch) } }
                    return@launch
                }
                reconnectPolicy.onAttemptFinished(generation, success = true, retryOnFailure = retryOnFailure)
                _uiState.update {
                    it.copy(
                        connecting = false,
                        connected = true,
                        session = session,
                        showInterfaceDialog = showDialog,
                        events = initialEvents,
                        savedConnectionId = params.savedConnectionId,
                        reconnectingMessage = null,
                        localUiPort = tunnelManager.localUiPort,
                        navigateToDashboard = true
                    )
                }
                showToast("Connection Successful")
                connectEventStream(client, generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (!reconnectPolicy.isCurrent(generation)) return@launch
                // Drop this attempt's tunnel before finishing so its disconnect
                // callback still sees attemptOpen and cannot schedule a second retry.
                // releaseEpoch does nothing if a newer attempt already owns the session.
                val epoch = opened?.epoch
                if (epoch != null) {
                    withContext(Dispatchers.IO) { runCatching { tunnelManager.releaseEpoch(epoch) } }
                }
                if (!reconnectPolicy.isCurrent(generation)) return@launch
                val errorMsg = e.message ?: "Connection failed"
                val action = reconnectPolicy.onAttemptFinished(
                    generation,
                    success = false,
                    retryOnFailure = retryOnFailure,
                    retryable = isRetryableConnectionFailure(errorMsg)
                )
                when (action) {
                    ReconnectAction.Schedule -> {
                        _uiState.update { it.copy(connecting = false, connected = false) }
                        scheduleReconnect(generation)
                    }
                    ReconnectAction.GiveUp -> showConnectionFailure(
                        "$errorMsg Stopped after ${ReconnectPolicy.MAX_AUTOMATIC_RECONNECTS} reconnect attempts."
                    )
                    else -> showConnectionFailure(errorMsg)
                }
            }
        }
    }

    private fun onTransportLost(eventGeneration: Int) {
        when (reconnectPolicy.onTransportLost(eventGeneration)) {
            ReconnectAction.Schedule -> viewModelScope.launch { scheduleReconnect(eventGeneration) }
            ReconnectAction.GiveUp -> viewModelScope.launch {
                showConnectionFailure(
                    "Connection dropped. Stopped after ${ReconnectPolicy.MAX_AUTOMATIC_RECONNECTS} reconnect attempts."
                )
            }
            else -> Unit
        }
    }

    private fun scheduleReconnect(generation: Int) {
        // A loss posted before the user pressed Connect must not occupy reconnectJob.
        if (!reconnectPolicy.isCurrent(generation)) return
        if (isIntentionalDisconnect || activeParams == null) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connected = false,
                    connecting = true,
                    error = null,
                    reconnectingMessage = "Connection dropped. Reconnecting in 3s..."
                )
            }
            delay(3000)
            val params = activeParams ?: return@launch
            if (reconnectPolicy.onDelayElapsed(generation) != ReconnectAction.Start) return@launch
            val next = reconnectPolicy.beginAttempt()
            launchConnection(params, next, retryOnFailure = true)
        }
    }

    private fun isRetryableConnectionFailure(message: String): Boolean {
        return !message.startsWith("Authentication failed") && !message.startsWith("Host key changed")
    }

    private fun showConnectionFailure(message: String) {
        _uiState.update {
            it.copy(connecting = false, connected = false, error = message, reconnectingMessage = null)
        }
        if (!message.startsWith("Host key changed")) {
            showToast("Connection Failed: $message")
        }
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        reconnectPolicy.markIntentionalDisconnect()
        activeParams = null
        reconnectJob?.cancel()
        reconnectJob = null
        connectionJob?.cancel()
        connectionJob = null
        val socket = webSocket
        webSocket = null
        // Reset connected synchronously so ConnectScreen doesn't re-trigger onConnected()
        // before the cleanup coroutine finishes.
        _uiState.value = AppUiState()
        viewModelScope.launch {
            socket?.close(1000, "Disconnected")
            locationTracker.stopTracking()
            withContext(Dispatchers.IO) { runCatching { tunnelManager.disconnect() } }
            apiClient = null
        }
    }

    fun refreshSession() {
        viewModelScope.launch {
            val client = apiClient ?: return@launch
            try {
                val session = withContext(Dispatchers.IO) { client.getSession() }
                _uiState.update { it.copy(session = session) }
            } catch (_: Exception) {}
        }
    }

    fun sendCommand(cmd: String) {
        val client = apiClient ?: return
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { state ->
                val history = if (state.commandHistory.firstOrNull() == trimmed) {
                    state.commandHistory
                } else {
                    (listOf(trimmed) + state.commandHistory).take(50)
                }
                state.copy(commandLoading = true, commandOutput = null, commandHistory = history)
            }
            try {
                val result = withContext(Dispatchers.IO) { client.runCommand(cmd) }
                _uiState.update { it.copy(commandLoading = false, commandOutput = result) }
                showToast("Sent: $cmd")
                refreshSession()
            } catch (e: Exception) {
                _uiState.update { it.copy(commandLoading = false, commandOutput = "Error: ${e.message}") }
                showToast("Failed to send command")
            }
        }
    }

    fun clearCommandOutput() = _uiState.update { it.copy(commandOutput = null) }

    fun runSilent(cmd: String) {
        val client = apiClient ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client.runCommand(cmd) }
                refreshSession()
            } catch (_: Exception) {}
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /** Called by ConnectScreen immediately after navigating to Dashboard, so the flag is cleared
     *  and Back-navigation to ConnectScreen doesn't re-trigger the navigate. */
    fun onNavigatedToDashboard() = _uiState.update { it.copy(navigateToDashboard = false) }

    fun loadCaplets() {
        viewModelScope.launch {
            _capletsState.update { it.copy(loading = true, error = null) }
            try {
                val raw = withContext(Dispatchers.IO) {
                    tunnelManager.exec("find /usr/local/share/bettercap/caplets ~/.bettercap -name '*.cap' 2>/dev/null")
                }
                val list = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
                _capletsState.update { it.copy(loading = false, caplets = list) }
            } catch (e: Exception) {
                _capletsState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun openCapletEditor(path: String) {
        viewModelScope.launch {
            _capletsState.update { it.copy(loading = true, error = null) }
            try {
                val content = withContext(Dispatchers.IO) {
                    tunnelManager.exec("cat '$path'")
                }
                _capletsState.update { it.copy(loading = false, editingCaplet = path, editingContent = content) }
            } catch (e: Exception) {
                _capletsState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun closeCapletEditor() {
        _capletsState.update { it.copy(editingCaplet = null, editingContent = null) }
    }

    fun saveCaplet(path: String, content: String) {
        viewModelScope.launch {
            _capletsState.update { it.copy(loading = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    tunnelManager.uploadContent(path, content)
                }
                _capletsState.update { it.copy(loading = false, editingCaplet = null, editingContent = null) }
                showToast("Saved $path")
            } catch (e: Exception) {
                _capletsState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun loadPcaps() {
        viewModelScope.launch {
            _pcapState.update { it.copy(loading = true, error = null) }
            try {
                val raw = withContext(Dispatchers.IO) {
                    tunnelManager.exec("find ~ /root /tmp -maxdepth 3 -name '*.pcap' 2>/dev/null")
                }
                val list = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
                _pcapState.update { it.copy(loading = false, pcaps = list) }
            } catch (e: Exception) {
                _pcapState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun downloadPcap(remotePath: String, localDir: java.io.File) {
        viewModelScope.launch {
            _pcapState.update { it.copy(downloadingFile = remotePath, error = null) }
            try {
                val downloaded = withContext(Dispatchers.IO) {
                    tunnelManager.downloadFile(remotePath, localDir)
                }
                _pcapState.update {
                    it.copy(
                        downloadingFile = null,
                        downloadedFiles = it.downloadedFiles + downloaded.absolutePath
                    )
                }
            } catch (e: Exception) {
                _pcapState.update { it.copy(downloadingFile = null, error = e.message) }
            }
        }
    }


    fun dismissInterfaceDialog() = _uiState.update { it.copy(showInterfaceDialog = false) }

    fun selectInterface(name: String) {
        val client = apiClient ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client.runCommand("set net.iface $name") }
                withContext(Dispatchers.IO) { client.runCommand("wifi.recon on") }
                refreshSession()
            } catch (_: Exception) {}
        }
        val connId = _uiState.value.savedConnectionId
        if (connId != null) {
            val updated = _savedConnections.value.map { conn ->
                if (conn.id == connId) conn.copy(defaultIface = name) else conn
            }
            persist(updated)
        }
        _uiState.update { it.copy(showInterfaceDialog = false) }
    }

    fun clearHostFingerprint(sshHost: String, sshPort: Int) {
        secureStorage.clearHostFingerprint("$sshHost:$sshPort")
    }

    fun upsertConnection(conn: SavedConnection) {
        val updated = _savedConnections.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == conn.id }
        if (idx >= 0) updated[idx] = conn else updated.add(0, conn)
        persist(updated)
    }

    fun deleteConnection(id: String) {
        persist(_savedConnections.value.filter { it.id != id })
    }

    fun wifiBleCheckCaplet() {
        viewModelScope.launch {
            _wifiBleState.update { it.copy(loading = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    tunnelManager.exec("test -f $WIFI_BLE_CAPLET_PATH && echo yes || echo no")
                }
                _wifiBleState.update { it.copy(loading = false, capletDeployed = result.trim() == "yes") }
            } catch (e: Exception) {
                _wifiBleState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun wifiBleDeployCaplet() {
        viewModelScope.launch {
            _wifiBleState.update { it.copy(loading = true, error = null) }
            try {
                val b64 = wifiBleCapletBase64()
                withContext(Dispatchers.IO) {
                    tunnelManager.exec("mkdir -p \${HOME}/.bettercap")
                    tunnelManager.exec("echo '$b64' | base64 -d > $WIFI_BLE_CAPLET_PATH")
                }
                _wifiBleState.update { it.copy(loading = false, capletDeployed = true) }
            } catch (e: Exception) {
                _wifiBleState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun wifiBleRemoveCaplet() {
        viewModelScope.launch {
            _wifiBleState.update { it.copy(loading = true, error = null) }
            try {
                withContext(Dispatchers.IO) { tunnelManager.exec("rm -f $WIFI_BLE_CAPLET_PATH") }
                _wifiBleState.update { it.copy(loading = false, capletDeployed = false, scanning = false) }
            } catch (e: Exception) {
                _wifiBleState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun wifiBleStartScan() {
        val client = apiClient ?: return
        viewModelScope.launch {
            _wifiBleState.update { it.copy(loading = true, error = null) }
            try {
                withContext(Dispatchers.IO) { client.runCommand("include $WIFI_BLE_CAPLET_PATH") }
                _wifiBleState.update { it.copy(loading = false, scanning = true) }
                refreshSession()
            } catch (e: Exception) {
                _wifiBleState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun wifiBleStopScan() {
        val client = apiClient ?: return
        viewModelScope.launch {
            _wifiBleState.update { it.copy(loading = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    client.runCommand("wifi.recon off")
                    client.runCommand("ble.recon off")
                }
                _wifiBleState.update { it.copy(loading = false, scanning = false) }
                refreshSession()
            } catch (e: Exception) {
                _wifiBleState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun airgeddonCheckPlugin() {
        viewModelScope.launch {
            _airgeddonState.update { it.copy(loading = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    tunnelManager.exec("test -f $AIRGEDDON_PLUGIN_PATH && echo yes || echo no")
                }
                _airgeddonState.update { it.copy(loading = false, pluginDeployed = result.trim() == "yes") }
            } catch (e: Exception) {
                _airgeddonState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun airgeddonDeployPlugin() {
        viewModelScope.launch {
            _airgeddonState.update { it.copy(loading = true, error = null) }
            try {
                val b64 = pluginBase64()
                withContext(Dispatchers.IO) {
                    tunnelManager.exec("mkdir -p \"\${HOME}/.airgeddon/plugins\"")
                    tunnelManager.exec("echo '$b64' | base64 -d > $AIRGEDDON_PLUGIN_PATH && chmod +x $AIRGEDDON_PLUGIN_PATH")
                }
                _airgeddonState.update { it.copy(loading = false, pluginDeployed = true) }
            } catch (e: Exception) {
                _airgeddonState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun airgeddonRemovePlugin() {
        viewModelScope.launch {
            _airgeddonState.update { it.copy(loading = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    tunnelManager.exec("rm -f $AIRGEDDON_PLUGIN_PATH")
                }
                _airgeddonState.update { it.copy(loading = false, pluginDeployed = false) }
            } catch (e: Exception) {
                _airgeddonState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun airgeddonRefreshEvents() {
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    tunnelManager.exec("cat $AIRGEDDON_LOG_PATH 2>/dev/null")
                }
                _airgeddonState.update { it.copy(events = parseAirgeddonEvents(raw)) }
            } catch (_: Exception) {}
        }
    }

    fun airgeddonClearLog() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    tunnelManager.exec("rm -f $AIRGEDDON_LOG_PATH")
                }
                _airgeddonState.update { it.copy(events = emptyList()) }
            } catch (_: Exception) {}
        }
    }

    private fun persist(list: List<SavedConnection>) {
        _savedConnections.value = list
        viewModelScope.launch(Dispatchers.IO) { repo.save(list) }
    }

    private fun connectEventStream(client: BettercapApiClient, generation: Int) {
        if (!reconnectPolicy.isCurrent(generation)) return
        webSocket = client.connectWebSocket(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!reconnectPolicy.isCurrent(generation)) return
                runCatching {
                    val event = gson.fromJson(text, BcEvent::class.java)
                    _uiState.update { state ->
                        val key = "${event.time}_${event.tag}"
                        if (state.events.any { "${it.time}_${it.tag}" == key }) state
                        else state.copy(events = (listOf(event) + state.events).take(300))
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onTransportLost(generation)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // 1000 is the close this client initiates before a new attempt.
                if (code != 1000) onTransportLost(generation)
            }
        })
    }

    override fun onCleared() {
        isIntentionalDisconnect = true
        reconnectPolicy.markIntentionalDisconnect()
        activeParams = null
        reconnectJob?.cancel()
        connectionJob?.cancel()
        val socket = webSocket
        webSocket = null
        socket?.close(1000, null)
        locationTracker.stopTracking()
        // viewModelScope is cancelled as this ViewModel clears, so the blocking
        // SSH close runs on a daemon thread and cannot ANR the main thread.
        val tunnel = tunnelManager
        Thread({ runCatching { tunnel.disconnect() } }, "bettercaper-tunnel-close").apply {
            isDaemon = true
            start()
        }
        super.onCleared()
    }
}
