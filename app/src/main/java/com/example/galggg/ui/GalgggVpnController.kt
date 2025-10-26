package com.example.galggg.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.example.galggg.singbox.SBClientOptions
import com.example.galggg.singbox.SBConstants
import com.example.galggg.vpn.GalgggVpnService
import com.galggg.ui.data.ConnectionState
import com.galggg.ui.data.Protocol
import com.galggg.ui.data.Server
import com.galggg.ui.data.VpnController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges UI events to the platform VPN service backed by sing-box.
 */
class GalgggVpnController(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VpnController {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serverMutex = Mutex()
    private val resolvedServers = ConcurrentHashMap<String, ResolvedServer>()

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = _state.asStateFlow()

    private val _server = MutableStateFlow<Server?>(null)
    override val currentServer = _server.asStateFlow()

    private var timeoutJob: Job? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null || intent.action != GalgggVpnService.ACTION_STATUS) return
            val rawState = intent.getStringExtra(GalgggVpnService.EXTRA_STATE) ?: return
            val newState = rawState.toConnectionState() ?: return
            val serverId = intent.getStringExtra(GalgggVpnService.EXTRA_SERVER_ID)
            val error = intent.getStringExtra(GalgggVpnService.EXTRA_ERROR)
            Log.d(TAG, "status <- state=$rawState server=$serverId error=$error")
            when (newState) {
                ConnectionState.CONNECTED -> {
                    timeoutJob?.cancel()
                    serverId?.let { setActiveServer(it) }
                    _state.value = ConnectionState.CONNECTED
                }
                ConnectionState.DISCONNECTED -> {
                    timeoutJob?.cancel()
                    _state.value = ConnectionState.DISCONNECTED
                }
                ConnectionState.ERROR -> {
                    timeoutJob?.cancel()
                    _state.value = ConnectionState.ERROR
                    if (!error.isNullOrEmpty()) {
                        Log.e(TAG, "backend error: $error")
                    }
                }
                ConnectionState.CONNECTING -> _state.value = ConnectionState.CONNECTING
                ConnectionState.RECONNECTING -> _state.value = ConnectionState.RECONNECTING
            }
        }
    }

    init {
        registerStatusReceiver()
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter(GalgggVpnService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(statusReceiver, filter)
        }
    }

    override suspend fun connect(serverId: String, protocol: Protocol) {
        if (VpnService.prepare(appContext) != null) {
            Log.w(TAG, "VPN permission not granted; aborting connect")
            _state.value = ConnectionState.ERROR
            return
        }

        val resolved = ensureServer(serverId)
        if (resolved == null) {
            Log.e(TAG, "Server $serverId not found")
            _state.value = ConnectionState.ERROR
            return
        }

        val mode = protocol.toCoreMode() ?: run {
            Log.w(TAG, "Unsupported protocol $protocol")
            _state.value = ConnectionState.ERROR
            return
        }

        if (!resolved.supportedProtocols.contains(mode)) {
            Log.w(TAG, "Server $serverId does not support protocol $mode")
            _state.value = ConnectionState.ERROR
            return
        }

        _server.value = resolved.ui
        _state.value = ConnectionState.CONNECTING
        scheduleTimeout(resolved.ui.id)

        try {
            startVpnService(resolved, mode)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start VPN service", t)
            timeoutJob?.cancel()
            _state.value = ConnectionState.ERROR
        }
    }

    override suspend fun disconnect() {
        timeoutJob?.cancel()
        val intent = Intent(appContext, GalgggVpnService::class.java).apply {
            action = GalgggVpnService.ACTION_STOP
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to stop VPN service", t)
            _state.value = ConnectionState.ERROR
        }
    }

    override suspend fun refreshServers(): List<Server> = serverMutex.withLock {
        val resolved = fetchServersLocked()
        resolved.map { it.ui }
    }

    private suspend fun ensureServer(serverId: String): ResolvedServer? {
        resolvedServers[serverId]?.let { return it }
        return serverMutex.withLock {
            resolvedServers[serverId] ?: run {
                val refreshed = fetchServersLocked()
                refreshed.firstOrNull { it.ui.id == serverId }
            }
        }
    }

    private suspend fun fetchServersLocked(): List<ResolvedServer> {
        val servers = withContext(ioDispatcher) { loadServersFromSource() }
        resolvedServers.clear()
        servers.forEach { resolvedServers[it.ui.id] = it }
        return servers
    }

    private suspend fun loadServersFromSource(): List<ResolvedServer> {
        return listOf(buildDefaultServer())
    }

    private fun buildDefaultServer(): ResolvedServer {
        val options = SBClientOptions(
            SBConstants.SERVER_HOST,
            SBConstants.SERVER_PORT,
            SBConstants.METHOD,
            SBConstants.PASSWORD_B64
        )
        val server = Server(
            id = "ss2022-${options.serverHost}:${options.serverPort}",
            country = "Default",
            city = "Core Node",
            flagEmoji = "",
            pingMs = estimatePing(options.serverHost)
        )
        return ResolvedServer(server, options, setOf(PROTOCOL_SS2022))
    }

    private fun estimatePing(host: String): Int {
        return try {
            val start = System.nanoTime()
            val reachable = InetAddress.getByName(host).isReachable(PING_TIMEOUT_MS)
            if (reachable) {
                (((System.nanoTime() - start) / 1_000_000L).toInt()).coerceAtLeast(1)
            } else {
                PING_UNAVAILABLE
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Ping check failed for $host: ${t.message}")
            PING_UNAVAILABLE
        }
    }

    private fun scheduleTimeout(serverId: String) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.RECONNECTING) {
                Log.e(TAG, "Connection to $serverId timed out")
                _state.value = ConnectionState.ERROR
            }
        }
    }

    private fun startVpnService(resolved: ResolvedServer, mode: String) {
        val intent = Intent(appContext, GalgggVpnService::class.java).apply {
            action = GalgggVpnService.ACTION_START
            putExtra(GalgggVpnService.EXTRA_SERVER_ID, resolved.ui.id)
            putExtra(GalgggVpnService.EXTRA_PROTOCOL, mode)
            putExtra(GalgggVpnService.EXTRA_SERVER_HOST, resolved.options.serverHost)
            putExtra(GalgggVpnService.EXTRA_SERVER_PORT, resolved.options.serverPort)
            putExtra(GalgggVpnService.EXTRA_SERVER_METHOD, resolved.options.method)
            putExtra(GalgggVpnService.EXTRA_SERVER_PASSWORD, resolved.options.passwordBase64)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
        Log.i(TAG, "start command -> server=${resolved.ui.id}, protocol=$mode")
    }

    private fun setActiveServer(serverId: String) {
        resolvedServers[serverId]?.let {
            _server.value = it.ui
            return
        }
        scope.launch {
            serverMutex.withLock {
                resolvedServers[serverId]?.let { resolved -> _server.value = resolved.ui }
            }
        }
    }

    private fun Protocol.toCoreMode(): String? = when (this) {
        Protocol.WIREGUARD -> "wg"
        Protocol.OPENVPN -> "ovpn"
        Protocol.SHADOWSOCKS -> PROTOCOL_SS2022
    }

    private fun String.toConnectionState(): ConnectionState? =
        runCatching { ConnectionState.valueOf(this) }.getOrNull()

    private data class ResolvedServer(
        val ui: Server,
        val options: SBClientOptions,
        val supportedProtocols: Set<String>
    )

    companion object {
        private const val TAG = "GalgggVpnController"
        private const val PROTOCOL_SS2022 = "ss2022"
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val PING_TIMEOUT_MS = 1_500
        private const val PING_UNAVAILABLE = -1
    }
}
