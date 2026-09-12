package com.dropboxx.ui.receive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dropboxx.core.formatBytes
import com.dropboxx.model.IncomingRequest
import com.dropboxx.model.ItemKind
import com.dropboxx.ui.components.DeviceAvatar
import com.dropboxx.ui.components.itemIcon

@Composable
fun IncomingRequestDialog(request: IncomingRequest, onDecision: (accept: Boolean, trust: Boolean) -> Unit) {
    var trust by remember(request.sessionId) { mutableStateOf(false) }
    AlertDialog(
        // A stray click outside must not decline someone's transfer; only the buttons decide.
        onDismissRequest = { },
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(request.sender.deviceType, size = 36)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(request.sender.alias, style = MaterialTheme.typography.titleMedium)
                    Text("wants to send ${request.items.size} item${if (request.items.size == 1) "" else "s"} - ${formatBytes(request.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(request.items, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(itemIcon(item.kind, item.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (item.kind == ItemKind.FILE) item.name else item.content.orEmpty().lineSequence().first().take(80),
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (item.kind == ItemKind.FILE) Text(formatBytes(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = trust, onCheckedChange = { trust = it })
                    Text("Always accept from this device", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { Button(onClick = { onDecision(true, trust) }) { Text("Accept") } },
        dismissButton = { TextButton(onClick = { onDecision(false, false) }) { Text("Decline") } },
    )
}
