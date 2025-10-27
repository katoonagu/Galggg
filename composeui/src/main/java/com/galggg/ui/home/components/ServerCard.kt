package com.galggg.ui.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galggg.ui.R
import com.galggg.ui.data.Server
import com.galggg.ui.theme.VpnTheme

@Composable
fun ServerCard(
    currentServer: Server?,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Main card content
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 43.dp, bottomStart = 43.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 17.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_server),
                contentDescription = null,
                modifier = Modifier.size(33.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.server_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = stringResource(R.string.server_sub),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
        
        // Right panel with country indicator
        Column(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 43.dp, bottomEnd = 43.dp))
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            
            // Country code box (top)
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(23.dp)
                    .background(
                        color = Color(0xFF484848),
                        shape = RoundedCornerShape(5.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = isConnected, label = "country-code") { connected ->
                    Text(
                        text = if (connected) "" else stringResource(R.string.country_unknown),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Country name (middle)
            Crossfade(
                targetState = currentServer?.country to isConnected,
                label = "country-name"
            ) { (country, connected) ->
                Text(
                    text = when {
                        connected && country != null -> {
                            // Extract country code (first 2 letters)
                            country.take(2).uppercase()
                        }
                        !connected -> "CR"
                        else -> "?"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            
            // Indicator bar (bottom)
            Crossfade(targetState = isConnected, label = "indicator") { connected ->
                Box(
                    modifier = Modifier
                        .width(23.dp)
                        .height(4.dp)
                        .padding(bottom = 16.dp)
                        .background(
                            color = if (connected) Color(0xFF5dd3b3) else Color(0xFFdddddd),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181a1e)
@Composable
private fun ServerCardDisconnectedPreview() {
    VpnTheme(darkTheme = true) {
        ServerCard(
            currentServer = null,
            isConnected = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181a1e)
@Composable
private fun ServerCardConnectedPreview() {
    VpnTheme(darkTheme = true) {
        ServerCard(
            currentServer = Server("de-fra-1", "Germany", "Frankfurt", "🇩🇪", 32),
            isConnected = true
        )
    }
}

