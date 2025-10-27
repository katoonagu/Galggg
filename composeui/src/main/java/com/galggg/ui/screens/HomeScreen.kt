package com.galggg.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galggg.ui.R
import com.galggg.ui.components.BigVpnButton
import com.galggg.ui.data.ConfigState
import com.galggg.ui.data.ConnectionState
import com.galggg.ui.data.Server
import com.galggg.ui.home.components.*
import com.galggg.ui.theme.ErrorTint
import com.galggg.ui.theme.VpnTheme

@Composable
fun HomeScreen(
    connectionState: ConnectionState,
    configState: ConfigState,
    currentServer: Server?,
    onPowerClick: () -> Unit,
    onConfigLoad: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.toString()?.let(onConfigLoad)
        }
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(44.dp))
            
            // Title
            Text(
                text = stringResource(R.string.app_title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Power button
            BigVpnButton(
                state = connectionState,
                configLoaded = configState == ConfigState.LOADED,
                onClick = onPowerClick
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status text
            val statusText = when {
                connectionState == ConnectionState.CONNECTED -> 
                    stringResource(R.string.msg_config_loaded)
                configState == ConfigState.LOADED && connectionState == ConnectionState.DISCONNECTED -> 
                    stringResource(R.string.msg_config_loaded)
                configState != ConfigState.LOADED -> 
                    stringResource(R.string.msg_need_config)
                else -> ""
            }
            
            val statusColor = when {
                configState != ConfigState.LOADED -> ErrorTint
                connectionState == ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.onBackground
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                ),
                color = statusColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Protected chip (visible when connected)
            ProtectedChip(
                visible = connectionState == ConnectionState.CONNECTED,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Config card
            ConfigCard(
                onClick = { configLauncher.launch("*/*") }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Server card
            ServerCard(
                currentServer = currentServer,
                isConnected = connectionState == ConnectionState.CONNECTED
            )
            
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Bottom navigation
        BottomNavBar(
            onHomeClick = { /* Already on home */ },
            onSettingsClick = onSettingsClick,
            onPremiumClick = onPremiumClick,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// Preview for disconnected state (no config)
@Preview(showBackground = true)
@Composable
private fun HomeScreenNoConfigPreview() {
    VpnTheme(darkTheme = true) {
        HomeScreen(
            connectionState = ConnectionState.DISCONNECTED,
            configState = ConfigState.NOT_LOADED,
            currentServer = null,
            onPowerClick = {},
            onConfigLoad = {},
            onSettingsClick = {},
            onPremiumClick = {}
        )
    }
}

// Preview for loaded config, disconnected
@Preview(showBackground = true)
@Composable
private fun HomeScreenConfigLoadedPreview() {
    VpnTheme(darkTheme = true) {
        HomeScreen(
            connectionState = ConnectionState.DISCONNECTED,
            configState = ConfigState.LOADED,
            currentServer = null,
            onPowerClick = {},
            onConfigLoad = {},
            onSettingsClick = {},
            onPremiumClick = {}
        )
    }
}

// Preview for connected state
@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectedPreview() {
    VpnTheme(darkTheme = true) {
        HomeScreen(
            connectionState = ConnectionState.CONNECTED,
            configState = ConfigState.LOADED,
            currentServer = Server("de-fra-1", "Germany", "Frankfurt", "🇩🇪", 32),
            onPowerClick = {},
            onConfigLoad = {},
            onSettingsClick = {},
            onPremiumClick = {}
        )
    }
}