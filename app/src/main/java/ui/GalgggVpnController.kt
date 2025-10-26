package .ui

import com.galggg.ui.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GalgggVpnController(
    // TODO: прокинь сюда зависимости твоего ядра/сервиса (sing-box/ss2022)
) : VpnController {

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = _state.asStateFlow()

    private val _server = MutableStateFlow<Server?>(null)
    override val currentServer = _server.asStateFlow()

    override suspend fun connect(serverId: String, protocol: Protocol) {
        _state.value = ConnectionState.CONNECTING
        // TODO: вызов твоего бэкенда. Пример (псевдокод):
        // core.connect(serverId, when(protocol){
        //   Protocol.WIREGUARD -> "wg"
        //   Protocol.OPENVPN -> "ovpn"
        //   Protocol.SHADOWSOCKS -> "ss2022"
        // })
        // Подписка на события статуса ядра -> маппинг в _state/_server
        _server.value = sampleServers().firstOrNull { it.id == serverId }
        _state.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        // core.disconnect()
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun refreshServers(): List<Server> {
        // TODO: вернуть реальные сервера из бэка; пока мок:
        return sampleServers()
    }
}
