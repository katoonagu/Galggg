package com.galggg.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.galggg.ui.R
import com.galggg.ui.data.ConnectionState
import com.galggg.ui.theme.*

@Composable
fun BigVpnButton(
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isConnecting: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    
    val outerRingColor by animateColorAsState(
        targetValue = when {
            !enabled -> VpnButtonDisconnectedOuter.copy(alpha = 0.5f)
            isConnected -> VpnButtonConnectedOuter
            else -> VpnButtonDisconnectedOuter
        },
        label = "outer-ring"
    )
    
    val innerRingColor by animateColorAsState(
        targetValue = when {
            !enabled -> VpnButtonDisconnectedInner.copy(alpha = 0.5f)
            isConnected -> VpnButtonConnectedInner
            else -> VpnButtonDisconnectedInner
        },
        label = "inner-ring"
    )
    
    // Pulsing animation for connecting state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )
    
    Box(
        modifier = modifier
            .size(251.dp)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(251.dp)
                .border(
                    width = 35.dp,
                    color = outerRingColor.copy(alpha = if (isConnecting) pulseAlpha else 1f),
                    shape = CircleShape
                )
        )
        
        // Inner circle
        Box(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .border(
                    width = 0.dp,
                    color = Color.Transparent,
                    shape = CircleShape
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = innerRingColor)
            }
            
            // Power icon
            Icon(
                painter = painterResource(id = R.drawable.ic_power),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.Center),
                tint = Color.Black.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun BigVpnButton(
    state: ConnectionState,
    configLoaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BigVpnButton(
        isConnected = state == ConnectionState.CONNECTED,
        isConnecting = state == ConnectionState.CONNECTING || state == ConnectionState.RECONNECTING,
        enabled = configLoaded,
        onClick = onClick,
        modifier = modifier
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF181a1e)
@Composable
private fun BigVpnButtonDisconnectedPreview() {
    VpnTheme(darkTheme = true) {
        BigVpnButton(
            isConnected = false,
            onClick = {},
            enabled = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181a1e)
@Composable
private fun BigVpnButtonConnectedPreview() {
    VpnTheme(darkTheme = true) {
        BigVpnButton(
            isConnected = true,
            onClick = {},
            enabled = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181a1e)
@Composable
private fun BigVpnButtonDisabledPreview() {
    VpnTheme(darkTheme = true) {
        BigVpnButton(
            isConnected = false,
            onClick = {},
            enabled = false
        )
    }
}