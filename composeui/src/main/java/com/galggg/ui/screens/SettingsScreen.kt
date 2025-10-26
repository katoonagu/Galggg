package com.galggg.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Divider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
        SegmentedButton(protocol, onProtocolChange)
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
private fun SegmentedButton(selected: Protocol, onSelected: (Protocol) -> Unit) {
    val items = listOf(Protocol.WIREGUARD, Protocol.OPENVPN, Protocol.SHADOWSOCKS)
    SingleChoiceSegmentedButtonRow {
        items.forEachIndexed { index, p ->
            val position = when (index) {
                0 -> SegmentedButtonDefaults.ItemPosition.Start
                items.lastIndex -> SegmentedButtonDefaults.ItemPosition.End
                else -> SegmentedButtonDefaults.ItemPosition.Middle
            }
            SegmentedButton(
                shape = SegmentedButtonDefaults.shape(position = position),
                selected = p == selected,
                onClick = { onSelected(p) },
                label = { Text(p.name) }
            )
        }
    }
}
