package com.galggg.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Home
import androidx.compose.material3.icons.filled.List
import androidx.compose.material3.icons.filled.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.galggg.ui.screens.*
import com.galggg.ui.vm.*
import com.galggg.ui.data.*

enum class Dest(val route: String) { Home("home"), Locations("locations"), Settings("settings") }

@Composable
fun VpnApp(controller: VpnController = InMemoryVpnController()) {
    val nav = rememberNavController()
    val homeVm = remember { HomeViewModel(controller) }
    val locVm = remember { LocationsViewModel(controller) }
    VpnScaffold(
        content = { current ->
            NavHost(navController = nav, startDestination = Dest.Home.route) {
                composable(Dest.Home.route) {
                    val ui by homeVm.ui.collectAsState()
                    HomeScreen(
                        state = ui.connectionState,
                        currentServer = ui.currentServer,
                        onConnectToggle = {
                            if (ui.connectionState == ConnectionState.CONNECTED) homeVm.disconnect()
                            else homeVm.connect((ui.currentServer?.id) ?: "us-nyc-1")
                        },
                        onPickLocation = { nav.navigate(Dest.Locations.route) }
                    )
                }
                composable(Dest.Locations.route) {
                    val ui by locVm.ui.collectAsState()
                    LocationsScreen(
                        servers = ui.servers,
                        onSelect = { s ->
                            homeVm.connect(s.id)
                            nav.popBackStack()
                        }
                    )
                }
                composable(Dest.Settings.route) {
                    val ui by homeVm.ui.collectAsState()
                    var kill by remember { mutableStateOf(false) }
                    var auto by remember { mutableStateOf(true) }
                    SettingsScreen(
                        protocol = ui.protocol,
                        onProtocolChange = { homeVm.setProtocol(it) },
                        killSwitch = kill,
                        onKillSwitchChange = { kill = it },
                        autoConnect = auto,
                        onAutoConnectChange = { auto = it }
                    )
                }
            }
        },
        onNavigate = { dest -> nav.navigate(dest.route) },
        currentDestination = nav.currentBackStackEntryAsState().value?.destination?.route
    )
}

@Composable
private fun VpnScaffold(
    content: @Composable (String?) -> Unit,
    onNavigate: (Dest) -> Unit,
    currentDestination: String?
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination == Dest.Home.route,
                    onClick = { onNavigate(Dest.Home) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentDestination == Dest.Locations.route,
                    onClick = { onNavigate(Dest.Locations) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Locations") }
                )
                NavigationBarItem(
                    selected = currentDestination == Dest.Settings.route,
                    onClick = { onNavigate(Dest.Settings) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            content(currentDestination)
        }
    }
}