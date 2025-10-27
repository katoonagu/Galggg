package com.galggg.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galggg.ui.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val configState: ConfigState = ConfigState.NOT_LOADED,
    val currentServer: Server? = null,
    val isPremium: Boolean = false,
    val protocol: Protocol = Protocol.WIREGUARD
)

class HomeViewModel(private val controller: VpnController) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            controller.connectionState.collect { s ->
                _ui.update { it.copy(connectionState = s) }
            }
        }
        viewModelScope.launch {
            controller.currentServer.collect { srv ->
                _ui.update { it.copy(currentServer = srv) }
            }
        }
    }

    fun setProtocol(p: Protocol) {
        _ui.update { it.copy(protocol = p) }
    }

    fun loadConfig(uri: String) {
        // Mark as loading
        _ui.update { it.copy(configState = ConfigState.LOADING) }
        // For MVP: simply mark as loaded after selection
        // In production: parse/validate the config file
        viewModelScope.launch {
            try {
                // Simulate config loading/parsing
                kotlinx.coroutines.delay(500)
                _ui.update { it.copy(configState = ConfigState.LOADED) }
            } catch (e: Exception) {
                _ui.update { it.copy(configState = ConfigState.ERROR) }
            }
        }
    }

    fun onConfigLoaded() {
        _ui.update { it.copy(configState = ConfigState.LOADED) }
    }

    fun onConfigError() {
        _ui.update { it.copy(configState = ConfigState.ERROR) }
    }

    fun toggleConnection() = viewModelScope.launch {
        if (_ui.value.configState != ConfigState.LOADED) {
            return@launch
        }
        
        when (_ui.value.connectionState) {
            ConnectionState.CONNECTED -> disconnect()
            ConnectionState.DISCONNECTED -> {
                // Use first available server or default
                val serverId = _ui.value.currentServer?.id ?: "de-fra-1"
                connect(serverId)
            }
            else -> {} // Ignore clicks during transitional states
        }
    }

    fun connect(serverId: String) = viewModelScope.launch {
        controller.connect(serverId, _ui.value.protocol)
    }

    fun disconnect() = viewModelScope.launch { controller.disconnect() }
}

data class LocationsUiState(
    val servers: List<Server> = emptyList(),
    val query: String = ""
)

class LocationsViewModel(private val controller: VpnController): ViewModel() {
    private val _ui = MutableStateFlow(LocationsUiState())
    val ui: StateFlow<LocationsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val servers = controller.refreshServers()
        _ui.update { it.copy(servers = servers) }
    }

    fun search(q: String) {
        _ui.update { it.copy(query = q) }
    }
}