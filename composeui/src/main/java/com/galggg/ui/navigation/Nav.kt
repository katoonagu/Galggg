package com.galggg.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.galggg.ui.data.ConnectionState
import com.galggg.ui.data.InMemoryVpnController
import com.galggg.ui.data.VpnController
import com.galggg.ui.screens.HomeScreen
import com.galggg.ui.screens.LocationsScreen
import com.galggg.ui.screens.SettingsScreen
import com.galggg.ui.vm.HomeViewModel
import com.galggg.ui.vm.LocationsViewModel

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
                        connectionState = ui.connectionState,
                        configState = ui.configState,
                        currentServer = ui.currentServer,
                        onPowerClick = { homeVm.toggleConnection() },
                        onConfigLoad = { uri -> homeVm.loadConfig(uri) },
                        onSettingsClick = { nav.navigate(Dest.Settings.route) },
                        onPremiumClick = { /* TODO: Navigate to premium screen */ }
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
