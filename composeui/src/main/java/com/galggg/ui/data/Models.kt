package com.galggg.ui.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class Server(
    val id: String,
    val country: String,
    val city: String,
    val flagEmoji: String,
    val pingMs: Int,
    val isPremium: Boolean = false,
    val isFavorite: Boolean = false
)

enum class Protocol { WIREGUARD, OPENVPN, SHADOWSOCKS }

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
}

enum class ConfigState {
    NOT_LOADED,      // No config file chosen
    LOADING,         // File picker active
    LOADED,          // Config successfully loaded
    ERROR            // Failed to load/parse config
}

interface VpnController {
    val connectionState: Flow<ConnectionState>
    val currentServer: Flow<Server?>
    suspend fun connect(serverId: String, protocol: Protocol)
    suspend fun disconnect()
    suspend fun refreshServers(): List<Server>
}

// Stub implementation to make UI previewable
class InMemoryVpnController : VpnController {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _server = MutableStateFlow<Server?>(null)
    override val connectionState: Flow<ConnectionState> = _state.asStateFlow()
    override val currentServer: Flow<Server?> = _server.asStateFlow()

    override suspend fun connect(serverId: String, protocol: Protocol) {
        _state.value = ConnectionState.CONNECTING
        // fake delay omitted
        _server.value = sampleServers().firstOrNull { it.id == serverId }
        _state.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun refreshServers(): List<Server> = sampleServers()
}

fun sampleServers(): List<Server> = listOf(
    Server("us-nyc-1", "USA", "New York", "🇺🇸", 22),
    Server("de-fra-1", "Germany", "Frankfurt", "🇩🇪", 32),
    Server("jp-tyo-1", "Japan", "Tokyo", "🇯🇵", 80, isPremium = true),
    Server("nl-ams-1", "Netherlands", "Amsterdam", "🇳🇱", 45),
    Server("ua-kyiv-1", "Ukraine", "Kyiv", "🇺🇦", 48)
)