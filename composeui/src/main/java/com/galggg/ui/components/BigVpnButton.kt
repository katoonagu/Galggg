package com.galggg.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BigVpnButton(
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (isConnected) "DISCONNECT" else "CONNECT"
    val ringAlpha by animateFloatAsState(targetValue = if (isConnected) 0.15f else 0.05f, label = "ring")

    Box(
        modifier = modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isConnected)
                        listOf(Color(0xFF12D18E), Color(0xFF0E7F60))
                    else
                        listOf(Color(0xFF4C9FFF), Color(0xFF1E3A8A)),
                    center = Offset(90f, 90f),
                    radius = 240f
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // inner glow ring
            drawCircle(color = Color.Black, radius = size.minDimension/2.2f, alpha = ringAlpha)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}