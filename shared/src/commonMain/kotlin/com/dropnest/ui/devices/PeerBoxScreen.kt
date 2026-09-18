package com.dropnest.ui.devices

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropnest.core.formatBytes
import com.dropnest.model.BoxEntry
import com.dropnest.model.ItemKind
import com.dropnest.ui.components.AccentTile
import com.dropnest.ui.components.EmptyNote
import com.dropnest.ui.components.HSpace
import com.dropnest.ui.components.ItemRow
import com.dropnest.ui.components.Muted
import com.dropnest.ui.components.NButton
import com.dropnest.ui.components.NButtonStyle
import com.dropnest.ui.components.NCard
import com.dropnest.ui.components.NIconButton
import com.dropnest.ui.components.PinDialog
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.components.itemMeta
import com.dropnest.ui.components.nestIcon
import com.dropnest.ui.components.phIcon
import com.dropnest.ui.motion.rise
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PeerBoxScreen(peerId: String, wide: Boolean, onBack: () -> Unit, onMessage: (String) -> Unit, viewModel: PeerBoxViewModel = koinViewModel { parametersOf(peerId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }
    // Quiet poll while this screen is open; the visit token means the owner is never asked again.
    LaunchedEffect(viewModel) { while (true) { delay(6_000); viewModel.autoRefresh() } }
    val peer = state.peer
    val t = N
    val ready = state.status as? PeerBoxStatus.Ready
    val count = ready?.items?.size ?: 0

    Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 24.dp else 14.dp, vertical = if (wide) 18.dp else 6.dp)) {
        val actions: @Composable () -> Unit = {
            if (ready != null) {
                if (state.refreshing) Box(Modifier.size(if (wide) 32.dp else 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = t.accent) }
                else NIconButton(Ph.ArrowsClockwise, { viewModel.refresh() }, size = if (wide) 32.dp else 40.dp, iconSize = 16.dp, contentDescription = "Refresh")
                HSpace(8.dp)
            }
            if (ready != null && ready.items.isNotEmpty()) {
                if (state.selected.isEmpty()) NButton("Select all", viewModel::selectAll, style = NButtonStyle.Ghost, fontSize = if (wide) 12 else 11)
                else NButton("Deselect", viewModel::clearSelection, style = NButtonStyle.Ghost, fontSize = if (wide) 12 else 11)
                HSpace(8.dp)
                NButton(
                    if (state.selected.isEmpty()) (if (wide) "Fetch all to my box" else "Fetch all") else "Fetch ${state.selected.size}",
                    viewModel::fetch, icon = Ph.ArrowDown, style = NButtonStyle.Primary,
                    padding = PaddingValues(12.dp, if (wide) 7.dp else 10.dp),
                )
            }
            if (ready == null) NIconButton(Ph.Wifi, { viewModel.load() }, size = if (wide) 32.dp else 40.dp, contentDescription = "Reload")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NIconButton(Ph.CaretLeft, onBack, size = if (wide) 32.dp else 40.dp, iconSize = 16.dp, contentDescription = "Back")
            HSpace(if (wide) 12.dp else 9.dp)
            if (wide && peer != null) { AccentTile(peer.info.deviceType.phIcon(), size = 38.dp, iconSize = 20.dp, radius = 10.dp); HSpace(12.dp) }
            Column(Modifier.weight(1f)) {
                Text(peer?.info?.alias ?: "Device", color = t.text, fontSize = if (wide) 21.sp else 16.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(if (peer == null) "Not nearby" else if (ready != null) "Their box · $count item${if (count == 1) "" else "s"} shared with you" else "Their box", 11, maxLines = 1)
            }
            if (wide) actions()
        }
        if (!wide && peer != null) {
            Row(Modifier.padding(4.dp, 14.dp, 0.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AccentTile(peer.info.deviceType.phIcon(), size = 48.dp, iconSize = 24.dp, radius = 13.dp)
                HSpace(12.dp)
                Muted("Dropped for you · stays on their device until you fetch it.", 12, Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Spacer(Modifier.weight(1f)); actions()
            }
        }
        VSpace(if (wide) 16.dp else 4.dp)
        when (val s = state.status) {
            PeerBoxStatus.Loading -> Centered { CircularProgressIndicator(color = t.accent) }
            PeerBoxStatus.WaitingForApproval -> Centered {
                CircularProgressIndicator(color = t.accent); VSpace(12.dp)
                Text("Waiting for ${peer?.info?.alias ?: "the other device"} to let you in…", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Muted("They see a prompt on their screen.", 12)
            }
            PeerBoxStatus.Denied -> EmptyNote("Access denied", "${peer?.info?.alias ?: "The other device"} did not allow you to open their box.")
            PeerBoxStatus.Busy -> EmptyNote("Busy", "They are answering another request. Try again in a moment.")
            is PeerBoxStatus.Failed -> EmptyNote("Could not open the box", s.message)
            PeerBoxStatus.PinRequired -> if (peer != null) PinDialog(peer, onDismiss = onBack) { pin -> viewModel.load(pin) }
            is PeerBoxStatus.Ready -> if (s.items.isEmpty()) EmptyNote("Their box is empty", "Nothing has been dropped on that device yet.")
                else if (wide) DesktopGrid(s, state.selected, viewModel) else PhoneList(s, state.selected, viewModel)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
}

@Composable
private fun DesktopGrid(ready: PeerBoxStatus.Ready, selected: Set<String>, vm: PeerBoxViewModel) {
    val t = N
    LazyVerticalGrid(GridCells.Adaptive(210.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(ready.items, key = { _, e -> e.id }) { i, e ->
            val on = e.id in selected
            NCard(Modifier.rise(i, e.id), radius = 13.dp, lift = true, padding = PaddingValues(13.dp), onClick = { vm.toggle(e.id) }) {
                Box(
                    Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(9.dp))
                        .background(Brush.linearGradient(listOf(t.accentSoft, t.bg2))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(nestIcon(e.kind, e.mimeType), null, tint = t.accent, modifier = Modifier.size(30.dp))
                    Checkbox(on, { vm.toggle(e.id) }, colors = CheckboxDefaults.colors(checkedColor = t.accent, uncheckedColor = t.muted, checkmarkColor = t.bg), modifier = Modifier.align(Alignment.TopEnd))
                }
                VSpace(9.dp)
                Text(displayName(e), color = t.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(itemMeta(e.kind, e.size), 10)
                VSpace(9.dp)
                NButton(
                    if (e.kind == ItemKind.URL) "Open link" else "Fetch", { if (e.kind == ItemKind.URL) vm.openUrl(e.content.orEmpty()) else vm.fetchOne(e) },
                    Modifier.fillMaxWidth(), icon = if (e.kind == ItemKind.URL) Ph.Link else Ph.ArrowDown, fontSize = 11,
                )
            }
        }
    }
}

@Composable
private fun PhoneList(ready: PeerBoxStatus.Ready, selected: Set<String>, vm: PeerBoxViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        itemsIndexed(ready.items, key = { _, e -> e.id }) { i, e ->
            ItemRow(
                name = displayName(e), meta = itemMeta(e.kind, e.size), kind = e.kind, mimeType = e.mimeType,
                card = true, lift = true, tileSize = 56.dp, checked = e.id in selected, onChecked = { vm.toggle(e.id) },
                onClick = { vm.toggle(e.id) }, modifier = Modifier.rise(i, e.id),
            ) {
                NIconButton(
                    if (e.kind == ItemKind.URL) Ph.Link else Ph.ArrowDown,
                    { if (e.kind == ItemKind.URL) vm.openUrl(e.content.orEmpty()) else vm.fetchOne(e) },
                    size = 44.dp, iconSize = 18.dp, contentDescription = "Fetch",
                )
            }
        }
    }
}

private fun displayName(e: BoxEntry) = if (e.kind == ItemKind.TEXT) e.content.orEmpty().lineSequence().first().take(80) else e.name
