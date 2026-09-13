package com.dropnest.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropnest.domain.BoxAccess
import com.dropnest.domain.ServerState
import com.dropnest.model.Peer
import com.dropnest.ui.components.DeviceAvatar
import com.dropnest.ui.components.EmptyState
import com.dropnest.ui.components.SectionHeader
import com.dropnest.ui.theme.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DevicesScreen(wide: Boolean, onOpenPeer: (Peer) -> Unit, onMessage: (String) -> Unit, viewModel: DevicesViewModel = koinViewModel()) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val server by viewModel.serverState.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val hotspot by viewModel.hotspot.collectAsStateWithLifecycle()
    var addressDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeviceAvatar(me.deviceType, size = 52, highlighted = server is ServerState.Running)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(me.alias, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        when (val s = server) {
                            is ServerState.Running -> Text(
                                if (s.addresses.isEmpty()) "Visible - not connected to a network" else "Visible at ${s.addresses.joinToString()} : ${s.port}",
                                style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                            )
                            ServerState.Starting -> Text("Starting...")
                            ServerState.Stopped -> Text("Hidden - other devices cannot see you", color = MaterialTheme.colorScheme.error)
                            is ServerState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Switch(checked = server is ServerState.Running || server == ServerState.Starting, onCheckedChange = { viewModel.toggleServer() })
                }
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                    Text("Who can open my box", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = settings.boxAccess == BoxAccess.ASK, onClick = { viewModel.setBoxAccess(BoxAccess.ASK) }, label = { Text("Ask me") })
                        FilterChip(selected = settings.boxAccess == BoxAccess.TRUSTED_ONLY, onClick = { viewModel.setBoxAccess(BoxAccess.TRUSTED_ONLY) }, label = { Text("Trusted only") })
                        FilterChip(selected = settings.boxAccess == BoxAccess.EVERYONE, onClick = { viewModel.setBoxAccess(BoxAccess.EVERYONE) }, label = { Text("Anyone nearby") })
                    }
                }
            }
        }
        if (hotspot.supported) item { HotspotCard(hotspot, viewModel::toggleHotspot) }
        item {
            SectionHeader("Nearby devices") {
                TextButton(onClick = { addressDialog = true }) { Text("Add by IP") }
                if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Scan network") }
            }
        }
        if (peers.isEmpty()) item {
            EmptyState(AppIcons.Wifi, "No devices found yet", "Open DropNest on the other device on the same Wi-Fi. Tap refresh to scan, or add it by IP.")
        }
        items(peers, key = { it.id }) { peer -> PeerCard(peer, onOpen = { onOpenPeer(peer) }) }
    }

    if (addressDialog) AddressDialog(onDismiss = { addressDialog = false }) { viewModel.addByAddress(it); addressDialog = false }
}

@Composable
private fun PeerCard(peer: Peer, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceAvatar(peer.info.deviceType, highlighted = peer.trusted)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(peer.info.alias, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (peer.trusted) Icon(AppIcons.Shield, contentDescription = "Trusted", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                }
                Text("${peer.address} - tap to see what they dropped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HotspotCard(hotspot: com.dropnest.domain.HotspotState, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Tethering, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text("No Wi-Fi around? Hotspot mode", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(hotspot.active || hotspot.starting, { onToggle() })
            }
            Spacer(Modifier.height(6.dp))
            when {
                hotspot.active -> {
                    Text("Connect the other device to this network; it will then appear in its device list.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Network:  ${hotspot.ssid}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Text("Password: ${hotspot.password}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                }
                hotspot.starting -> Text("Starting hotspot...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                hotspot.error != null -> Text(hotspot.error, color = MaterialTheme.colorScheme.error)
                else -> Text("Turns this phone into a local network so a PC or another phone can connect directly - no router or internet needed.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddressDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect by IP address") },
        text = {
            Column {
                Text("Shown on the other device under Devices, e.g. 192.168.1.20 or 192.168.1.20:47843.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(address, { address = it.trim() }, label = { Text("IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(address) }, enabled = address.isNotBlank()) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
