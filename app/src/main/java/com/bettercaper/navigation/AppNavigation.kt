package com.bettercaper.navigation

import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bettercaper.ui.screens.AirgeddonScreen
import com.bettercaper.ui.screens.BluetoothScreen
import com.bettercaper.ui.screens.CommandScreen
import com.bettercaper.ui.screens.ConnectScreen
import com.bettercaper.ui.screens.CapletsScreen
import com.bettercaper.ui.screens.WifiBleScreen
import com.bettercaper.ui.screens.PcapScreen
import com.bettercaper.ui.screens.DashboardScreen
import com.bettercaper.ui.screens.EventsScreen
import com.bettercaper.ui.screens.ModulesScreen
import com.bettercaper.ui.screens.ProxyScreen
import com.bettercaper.ui.screens.SavedConnectionsScreen
import com.bettercaper.ui.screens.SpoofingScreen
import com.bettercaper.ui.screens.WebViewScreen
import com.bettercaper.ui.screens.WifiScreen
import com.bettercaper.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    data object Connect : Screen("connect")
    data object Dashboard : Screen("dashboard")
    data object Events : Screen("events")
    data object Command : Screen("command")
    data object Wifi : Screen("wifi")
    data object Bluetooth : Screen("bluetooth")
    data object Modules : Screen("modules")
    data object Spoofing : Screen("spoofing")
    data object Proxies : Screen("proxies")
    data object WebUi : Screen("webui")
    data object SavedConnections : Screen("saved_connections")
    data object Airgeddon : Screen("airgeddon")
    data object Caplets : Screen("caplets")
    data object WifiBle : Screen("wifi_ble")
    data object Pcaps : Screen("pcaps")
}

@Composable
fun AppNavigation(vm: MainViewModel = hiltViewModel()) {
    val navController: NavHostController = rememberNavController()
    val uiState by vm.uiState.collectAsState()
    val savedConnections by vm.savedConnections.collectAsState()
    val airgeddonState by vm.airgeddonState.collectAsState()
    val wifiBleState by vm.wifiBleState.collectAsState()

    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.toastEvents.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(navController = navController, startDestination = Screen.Connect.route) {
        composable(Screen.Connect.route) {
            ConnectScreen(
                uiState = uiState,
                hasSavedConnections = savedConnections.isNotEmpty(),
                onConnect = { params -> vm.connect(params) },
                onClearError = { vm.clearError() },
                onClearFingerprint = { host, port -> vm.clearHostFingerprint(host, port) },
                onNavigationConsumed = { vm.onNavigatedToDashboard() },
                onConnected = {
                    navController.navigate(Screen.Dashboard.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateSaved = { navController.navigate(Screen.SavedConnections.route) }
            )
        }
        composable(Screen.SavedConnections.route) {
            SavedConnectionsScreen(
                connections = savedConnections,
                uiState = uiState,
                onConnect = { params ->
                    vm.connect(params)
                    navController.navigate(Screen.Connect.route) {
                        popUpTo(Screen.Connect.route) { inclusive = true }
                    }
                },
                onUpsert = { vm.upsertConnection(it) },
                onDelete = { vm.deleteConnection(it) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                uiState = uiState,
                onRefresh = { vm.refreshSession() },
                onSelectInterface = { name -> vm.selectInterface(name) },
                onDismissInterfaceDialog = { vm.dismissInterfaceDialog() },
                onNavigateEvents = { navController.navigate(Screen.Events.route) },
                onNavigateCommand = { navController.navigate(Screen.Command.route) },
                onNavigateWifi = { navController.navigate(Screen.Wifi.route) },
                onNavigateBluetooth = { navController.navigate(Screen.Bluetooth.route) },
                onNavigateModules = { navController.navigate(Screen.Modules.route) },
                onNavigateSpoofing = { navController.navigate(Screen.Spoofing.route) },
                onNavigateProxies = { navController.navigate(Screen.Proxies.route) },
                onNavigateWebUi = { navController.navigate(Screen.WebUi.route) },
                onNavigateAirgeddon = { navController.navigate(Screen.Airgeddon.route) },
                onNavigateCaplets = { navController.navigate(Screen.Caplets.route) },
                onNavigateWifiBle = { navController.navigate(Screen.WifiBle.route) },
                onNavigatePcaps = { navController.navigate(Screen.Pcaps.route) },
                onExecuteCommand = { cmd ->
                    vm.sendCommand(cmd)
                    navController.navigate(Screen.Command.route)
                },
                onStartScan = { cmd ->
                    vm.sendCommand(cmd)
                    navController.navigate(Screen.Events.route)
                },
                onDisconnect = {
                    vm.disconnect()
                    navController.navigate(Screen.Connect.route) {
                        popUpTo(Screen.Connect.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Events.route) {
            EventsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Command.route) {
            CommandScreen(
                uiState = uiState,
                onSendCommand = { cmd -> vm.sendCommand(cmd) },
                onClearOutput = { vm.clearCommandOutput() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Wifi.route) {
            WifiScreen(
                uiState = uiState,
                onRefresh = { vm.refreshSession() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Bluetooth.route) {
            BluetoothScreen(
                uiState = uiState,
                onRefresh = { vm.refreshSession() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Modules.route) {
            ModulesScreen(
                uiState = uiState,
                onToggleModule = { name, turningOn ->
                    vm.runSilent("$name ${if (turningOn) "on" else "off"}")
                },
                onRunCommand = { cmd -> vm.runSilent(cmd) },
                onRefresh = { vm.refreshSession() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Spoofing.route) {
            SpoofingScreen(
                uiState = uiState,
                onToggleModule = { name, turningOn ->
                    vm.runSilent("$name ${if (turningOn) "on" else "off"}")
                },
                onRunCommand = { cmd -> vm.runSilent(cmd) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Proxies.route) {
            ProxyScreen(
                uiState = uiState,
                onToggleModule = { name, turningOn ->
                    vm.runSilent("$name ${if (turningOn) "on" else "off"}")
                },
                onRunCommand = { cmd -> vm.runSilent(cmd) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.WebUi.route) {
            WebViewScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Airgeddon.route) {
            AirgeddonScreen(
                state = airgeddonState,
                uiState = uiState,
                onCheckPlugin = { vm.airgeddonCheckPlugin() },
                onDeployPlugin = { vm.airgeddonDeployPlugin() },
                onRemovePlugin = { vm.airgeddonRemovePlugin() },
                onRefreshEvents = { vm.airgeddonRefreshEvents() },
                onClearLog = { vm.airgeddonClearLog() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.WifiBle.route) {
            WifiBleScreen(
                state = wifiBleState,
                uiState = uiState,
                onCheck = { vm.wifiBleCheckCaplet() },
                onDeploy = { vm.wifiBleDeployCaplet() },
                onRemove = { vm.wifiBleRemoveCaplet() },
                onStartScan = { vm.wifiBleStartScan() },
                onStopScan = { vm.wifiBleStopScan() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Caplets.route) {
            val capletsState by vm.capletsState.collectAsState()
            CapletsScreen(
                state = capletsState,
                uiState = uiState,
                onLoadCaplets = { vm.loadCaplets() },
                onRunCaplet = { path -> vm.sendCommand("include $path"); navController.navigate(Screen.Command.route) },
                onEditCaplet = { path -> vm.openCapletEditor(path) },
                onCloseEditor = { vm.closeCapletEditor() },
                onSaveCaplet = { path, content -> vm.saveCaplet(path, content) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Pcaps.route) {
            val pcapState by vm.pcapState.collectAsState()
            val context = LocalContext.current
            PcapScreen(
                state = pcapState,
                uiState = uiState,
                onLoadPcaps = { vm.loadPcaps() },
                onDownloadPcap = { path ->
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    if (dir != null) {
                        vm.downloadPcap(path, dir)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
