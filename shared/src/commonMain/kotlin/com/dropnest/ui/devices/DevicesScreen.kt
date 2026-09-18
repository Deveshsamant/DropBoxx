package com.dropnest.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropnest.domain.BoxAccess
import com.dropnest.domain.HotspotState
import com.dropnest.domain.ServerState
import com.dropnest.model.Peer
import com.dropnest.ui.components.AccentTile
import com.dropnest.ui.components.AddressDialog
import com.dropnest.ui.components.EmptyNote
import com.dropnest.ui.components.HSpace
import com.dropnest.ui.components.Heading
import com.dropnest.ui.components.Kicker
import com.dropnest.ui.components.Muted
import com.dropnest.ui.components.NButton
import com.dropnest.ui.components.NButtonStyle
import com.dropnest.ui.components.NCard
import com.dropnest.ui.components.NIconButton
import com.dropnest.ui.components.NSeg
import com.dropnest.ui.components.NSwitch
import com.dropnest.ui.components.OrbitField
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.components.Well
import com.dropnest.ui.components.phIcon
import com.dropnest.ui.motion.rememberSpin
import com.dropnest.ui.motion.rise
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DevicesScreen(wide: Boolean, motion: Boolean, onOpenPeer: (Peer) -> Unit, onMessage: (String) -> Unit, viewModel: DevicesViewModel = koinViewModel()) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val server by viewModel.serverState.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val hotspot by viewModel.hotspot.collectAsStateWithLifecycle()
    var addressDialog by remember { mutableStateOf(false) }
    val spin = rememberSpin(autoDegPerSec = 13.2f, enabled = motion)
    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }
    val visible = server is ServerState.Running || server == ServerState.Starting
    val t = N

    val accessSeg = @Composable { fill: Boolean ->
        NSeg(listOf(BoxAccess.ASK to "Ask me", BoxAccess.TRUSTED_ONLY to "Trusted", BoxAccess.EVERYONE to "Anyone"), settings.boxAccess, viewModel::setBoxAccess, fill = fill)
    }

    if (wide) {
        Column(Modifier.fillMaxSize().padding(24.dp, 20.dp, 24.dp, 22.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    Kicker("On this network"); VSpace(5.dp); Heading("Devices"); VSpace(3.dp)
                    Muted("${peers.size} nearby · you are ${if (visible) "visible as ${me.alias}" else "hidden"}", 12)
                }
                Spacer(Modifier.weight(1f))
                NButton("Add by IP", { addressDialog = true })
                HSpace(8.dp)
                if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.accent) else NIconButton(Ph.Wifi, viewModel::refresh, contentDescription = "Scan")
                HSpace(8.dp)
                NButton(if (visible) "Visible" else "Hidden", viewModel::toggleServer, icon = Ph.Eye, style = if (visible) NButtonStyle.Primary else NButtonStyle.Secondary)
            }
            VSpace(14.dp)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Well(Modifier.weight(1f).fillMaxSize(), glow = true) {
                    OrbitField(peers, spin, me.deviceType, me.alias, onOpenPeer, Modifier.fillMaxSize(), radius = 158f, motion = motion)
                }
                Column(Modifier.width(316.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    NCard(radius = 13.dp, padding = PaddingValues(13.dp)) {
                        Kicker("Nearby", accent = false, size = 10); VSpace(9.dp)
                        if (peers.isEmpty()) Muted("No devices yet. Open DropNest on another device on this Wi-Fi, or add it by IP.", 11)
                        peers.forEachIndexed { i, p -> PeerRow(p, compact = true, Modifier.rise(i, p.id)) { onOpenPeer(p) } }
                    }
                    NCard(radius = 13.dp, padding = PaddingValues(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ph.ShieldCheck, null, tint = t.accent, modifier = Modifier.size(15.dp)); HSpace(7.dp)
                            Text("Who can open my box", color = t.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        VSpace(7.dp)
                        Muted("Ask me shows a prompt each time. Trusted devices are the ones you allowed before.", 11)
                        VSpace(9.dp)
                        accessSeg(true)
                    }
                    if (hotspot.supported) HotspotCard(hotspot, viewModel::toggleHotspot)
                    Spacer(Modifier.weight(1f))
                    Well(radius = 12.dp) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ph.ShieldCheck, null, tint = t.accent, modifier = Modifier.size(17.dp)); HSpace(9.dp)
                            Muted("Transfers stay on your LAN and are encrypted end to end.", 11)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(18.dp, 8.dp, 18.dp, 0.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Kicker("Nearby"); VSpace(3.dp); Heading("Devices", 24); VSpace(2.dp)
                    Muted(if (visible) "Visible as ${me.alias}" else "Hidden — nobody can see you", 11)
                }
                NIconButton(Ph.Eye, viewModel::toggleServer, size = 44.dp, iconSize = 18.dp, style = if (visible) NButtonStyle.Primary else NButtonStyle.Secondary, contentDescription = "Visibility")
            }
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                OrbitField(peers, spin, me.deviceType, "You", onOpenPeer, Modifier.fillMaxSize(), radius = 118f, cardWidth = 140, hint = "", motion = motion)
                Muted("Drag to spin · tap a device to open its box", 10, Modifier.align(Alignment.BottomStart).padding(18.dp, 0.dp))
            }
            Well(Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item {
                        Row(Modifier.padding(6.dp, 13.dp, 0.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Kicker("${peers.size} nearby", accent = false, size = 11)
                            Spacer(Modifier.weight(1f))
                            NButton("Add by IP", { addressDialog = true }, style = NButtonStyle.Ghost, fontSize = 12, padding = PaddingValues(10.dp, 9.dp))
                            if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.accent) else NIconButton(Ph.Wifi, viewModel::refresh, size = 44.dp, iconSize = 18.dp, style = NButtonStyle.Ghost)
                        }
                    }
                    if (peers.isEmpty()) item { EmptyNote("No devices found yet", "Open DropNest on the other device on the same Wi-Fi. Tap the Wi-Fi icon to scan, or add it by IP.") }
                    itemsIndexed(peers, key = { _, p -> p.id }) { i, p -> PeerRow(p, compact = false, Modifier.rise(i, p.id)) { onOpenPeer(p) } }
                    item {
                        VSpace(6.dp)
                        NCard(radius = 13.dp, padding = PaddingValues(13.dp)) {
                            Text("Who can open my box", color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium); VSpace(8.dp)
                            accessSeg(true)
                        }
                    }
                    if (hotspot.supported) item { VSpace(4.dp); HotspotCard(hotspot, viewModel::toggleHotspot) }
                }
            }
        }
    }
    if (addressDialog) AddressDialog({ addressDialog = false }) { viewModel.addByAddress(it); addressDialog = false }
}

@Composable
private fun PeerRow(peer: Peer, compact: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = N
    NCard(modifier.fillMaxWidth(), radius = if (compact) 10.dp else 12.dp, color = if (compact) androidx.compose.ui.graphics.Color.Transparent else t.surface, elevation = if (compact) 0 else 1, onClick = onClick, padding = PaddingValues(if (compact) 9.dp else 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTile(peer.info.deviceType.phIcon(), size = if (compact) 32.dp else 38.dp, iconSize = 17.dp, radius = if (compact) 8.dp else 10.dp, muted = !peer.trusted)
            HSpace(10.dp)
            Column(Modifier.weight(1f)) {
                Text(peer.info.alias, color = t.text, fontSize = if (compact) 12.5.sp else 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, maxLines = 1)
                Text(if (peer.trusted) "Trusted · ${peer.address}" else "${peer.address} · tap to open", color = if (peer.trusted) t.accent else t.muted, fontSize = 10.5.sp, lineHeight = 13.sp, maxLines = 1)
            }
            Icon(Ph.CaretRight, null, tint = t.muted, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun HotspotCard(hotspot: HotspotState, onToggle: () -> Unit) {
    val t = N
    NCard(Modifier.fillMaxWidth(), radius = 13.dp, padding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ph.Wifi, null, tint = t.accent, modifier = Modifier.size(16.dp)); HSpace(8.dp)
            Text("No Wi-Fi around? Hotspot mode", color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            NSwitch(hotspot.active || hotspot.starting, { onToggle() })
        }
        VSpace(6.dp)
        when {
            hotspot.active -> {
                Muted("Connect the other device to this network; it then appears in its device list.", 11); VSpace(6.dp)
                Text("Network:  ${hotspot.ssid}", color = t.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Password: ${hotspot.password}", color = t.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            hotspot.starting -> Muted("Starting hotspot…", 11)
            hotspot.error != null -> Text(hotspot.error, color = t.bad, fontSize = 11.sp)
            else -> Muted("Turns this phone into a local network so a PC or another phone can connect directly — no router or internet needed.", 11)
        }
    }
}
