package com.dropboxx.ui.receive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dropboxx.model.AccessRequest
import com.dropboxx.ui.components.DeviceAvatar

/** A peer wants to browse this device's box. */
@Composable
fun AccessRequestDialog(request: AccessRequest, onDecision: (allow: Boolean, always: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(request.requester.deviceType, size = 36)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(request.requester.alias, style = MaterialTheme.typography.titleMedium)
                    Text("wants to open your box", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = { Text("They will see the list of items in your box and can download them. From ${request.address}.") },
        confirmButton = {
            Row {
                TextButton(onClick = { onDecision(true, true) }) { Text("Always allow") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { onDecision(true, false) }) { Text("Allow once") }
            }
        },
        dismissButton = { TextButton(onClick = { onDecision(false, false) }) { Text("Deny") } },
    )
}
