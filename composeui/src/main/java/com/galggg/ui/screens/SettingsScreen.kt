package com.galggg.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galggg.ui.data.Protocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    protocol: Protocol,
    onProtocolChange: (Protocol) -> Unit,
    killSwitch: Boolean,
    onKillSwitchChange: (Boolean) -> Unit,
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Protocol", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ProtocolSelector(
            selected = protocol,
            onChange = onProtocolChange
        )
        Spacer(Modifier.height(16.dp))
        SettingToggle("Kill Switch", killSwitch, onKillSwitchChange)
        SettingToggle("Auto-connect on launch", autoConnect, onAutoConnectChange)
    }
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
    Divider(Modifier.padding(vertical = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolSelector(
    selected: Protocol,
    onChange: (Protocol) -> Unit
) {
    val items = listOf(Protocol.WIREGUARD, Protocol.OPENVPN, Protocol.SHADOWSOCKS)
    SingleChoiceSegmentedButtonRow {
        items.forEachIndexed { index, protocol ->
            SegmentedButton(
                selected = protocol == selected,
                onClick = { onChange(protocol) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                label = { Text(protocol.name) }
            )
        }
    }
}
