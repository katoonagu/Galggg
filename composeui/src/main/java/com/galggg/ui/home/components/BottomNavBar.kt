package com.galggg.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.galggg.ui.R
import com.galggg.ui.theme.VpnTheme

@Composable
fun BottomNavBar(
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 45.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onHomeClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_home),
                contentDescription = stringResource(R.string.nav_home),
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        IconButton(onClick = onSettingsClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = stringResource(R.string.nav_settings),
                modifier = Modifier.size(39.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        IconButton(onClick = onPremiumClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_dollar),
                contentDescription = stringResource(R.string.nav_premium),
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomNavBarPreview() {
    VpnTheme(darkTheme = true) {
        BottomNavBar(
            onHomeClick = {},
            onSettingsClick = {},
            onPremiumClick = {}
        )
    }
}

