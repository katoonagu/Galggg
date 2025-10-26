package com.galggg.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galggg.ui.data.Server

@Composable
fun LocationsScreen(
    servers: List<Server>,
    onSelect: (Server) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(servers) { s ->
            ListItem(
                headlineContent = { Text(text = "%s %s".format(s.flagEmoji, s.city), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(text = s.country) },
                trailingContent = { Text(text = "%d ms".format(s.pingMs)) },
                modifier = Modifier.clickable { onSelect(s) }
            )
            Divider()
        }
    }
}