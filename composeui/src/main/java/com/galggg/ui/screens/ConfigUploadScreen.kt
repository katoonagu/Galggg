package com.galggg.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ConfigUploadScreen(
    onFilePicked: (String) -> Unit
) {
    var fileName by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            fileName = uri?.lastPathSegment
            if (uri != null) onFilePicked(uri.toString())
        }
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Import configuration", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("Supported: .json, .conf, .yaml", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { launcher.launch("*/*") }) {
            Text("Choose file")
        }
        fileName?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}