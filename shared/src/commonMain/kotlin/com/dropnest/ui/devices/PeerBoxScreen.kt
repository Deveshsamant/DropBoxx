package com.dropnest.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropnest.core.formatBytes
import com.dropnest.model.ItemKind
import com.dropnest.ui.box.FileRow
import com.dropnest.ui.box.PinDialog
import com.dropnest.ui.components.DeviceAvatar
import com.dropnest.ui.components.EmptyState
import com.dropnest.ui.components.SectionHeader
import com.dropnest.ui.theme.AppIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PeerBoxScreen(peerId: String, wide: Boolean, onBack: () -> Unit, onMessage: (String) -> Unit, viewModel: PeerBoxViewModel = koinViewModel { parametersOf(peerId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }
    val peer = state.peer

    Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 24.dp else 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            if (peer != null) DeviceAvatar(peer.info.deviceType, size = 36, highlighted = peer.trusted)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(peer?.info?.alias ?: "Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (peer != null) "${peer.address} - their box" else "Not nearby", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, contentDescription = "Reload") }
        }
        when (val s = state.status) {
            PeerBoxStatus.Loading -> Centered { CircularProgressIndicator() }
            PeerBoxStatus.WaitingForApproval -> Centered {
                CircularProgressIndicator(); Spacer(Modifier.height(12.dp))
                Text("Waiting for ${peer?.info?.alias ?: "the other device"} to let you in...", style = MaterialTheme.typography.titleMedium)
                Text("They see a prompt on their screen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PeerBoxStatus.Denied -> EmptyState(AppIcons.Shield, "Access denied", "${peer?.info?.alias ?: "The other device"} did not allow you to open their box.")
            PeerBoxStatus.Busy -> EmptyState(AppIcons.History, "Busy", "They are answering another request. Try again in a moment.")
            is PeerBoxStatus.Failed -> EmptyState(AppIcons.Wifi, "Could not open the box", s.message)
            PeerBoxStatus.PinRequired -> if (peer != null) PinDialog(peer, onDismiss = onBack) { pin -> viewModel.load(pin) }
            is PeerBoxStatus.Ready -> ReadyList(s, state.selected, viewModel)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
}

@Composable
private fun ReadyList(ready: PeerBoxStatus.Ready, selected: Set<String>, vm: PeerBoxViewModel) {
    if (ready.items.isEmpty()) {
        EmptyState(AppIcons.Inbox, "Their box is empty", "Nothing has been dropped on that device yet.")
        return
    }
    val total = ready.items.sumOf { it.size }
    SectionHeader(if (selected.isEmpty()) "${ready.items.size} item${if (ready.items.size == 1) "" else "s"} - ${formatBytes(total)}" else "${selected.size} selected") {
        if (selected.isEmpty()) TextButton(onClick = vm::selectAll) { Text("Select all") } else TextButton(onClick = vm::clearSelection) { Text("Deselect") }
        Button(onClick = vm::fetch) {
            Icon(AppIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(if (selected.isEmpty()) "Fetch all" else "Fetch selected")
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(ready.items, key = { it.id }) { entry ->
            FileRow(
                name = if (entry.kind == ItemKind.TEXT) entry.content.orEmpty() else entry.name,
                size = entry.size, kind = entry.kind, mimeType = entry.mimeType, previewModel = null,
                checked = entry.id in selected, onCheckedChange = { vm.toggle(entry.id) },
                onClick = { vm.toggle(entry.id) },
                trailing = {
                    TextButton(onClick = { if (entry.kind == ItemKind.URL) vm.openUrl(entry.content.orEmpty()) else vm.fetchOne(entry) }) {
                        Text(if (entry.kind == ItemKind.URL) "Open" else "Fetch")
                    }
                },
            )
        }
    }
}
