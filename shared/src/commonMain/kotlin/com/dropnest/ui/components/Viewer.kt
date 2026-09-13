package com.dropnest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

/** Full-screen in-app image viewer; anything else is handed to the system's app via "Open with". */
@Composable
fun ImageViewerDialog(model: Any, title: String, onOpenExternal: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f))) {
            AsyncImage(model = model, contentDescription = title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
            TextButton(onClick = onOpenExternal, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) { Text("Open with...", color = Color.White) }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
