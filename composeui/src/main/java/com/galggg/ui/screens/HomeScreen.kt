package com.galggg.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galggg.ui.components.BigVpnButton
import com.galggg.ui.data.ConnectionState
import com.galggg.ui.data.Server

@Composable
fun HomeScreen(
    state: ConnectionState,
    currentServer: Server?,
    onConnectToggle: () -> Unit,
    onPickLocation: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when(state) {
                ConnectionState.CONNECTED -> "Connected"
                ConnectionState.CONNECTING -> "Connecting…"
                ConnectionState.RECONNECTING -> "Reconnecting…"
                ConnectionState.ERROR -> "Error"
                else -> "Disconnected"
            },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(24.dp))
        BigVpnButton(isConnected = state == ConnectionState.CONNECTED, onClick = onConnectToggle)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onPickLocation) {
            Text(text = currentServer?.let { "Location: %s, %s".format(it.city, it.country) } ?: "Choose location")
        }
    }
}