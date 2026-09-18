package com.dropnest.ui.transfers

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropnest.core.formatBytes
import com.dropnest.core.formatEta
import com.dropnest.core.formatSpeed
import com.dropnest.domain.PlatformServices
import com.dropnest.model.Direction
import com.dropnest.model.ItemKind
import com.dropnest.model.ItemStatus
import com.dropnest.model.SessionStatus
import com.dropnest.model.TransferItem
import com.dropnest.model.TransferSession
import com.dropnest.ui.FileOpener
import com.dropnest.ui.components.AccentTile
import com.dropnest.ui.components.Conveyor
import com.dropnest.ui.components.EmptyNote
import com.dropnest.ui.components.Gauge
import com.dropnest.ui.components.HSpace
import com.dropnest.ui.components.Heading
import com.dropnest.ui.components.ItemRow
import com.dropnest.ui.components.Kicker
import com.dropnest.ui.components.Muted
import com.dropnest.ui.components.NButton
import com.dropnest.ui.components.NButtonStyle
import com.dropnest.ui.components.NCard
import com.dropnest.ui.components.NIconButton
import com.dropnest.ui.components.ProgressRing
import com.dropnest.ui.components.Sheen
import com.dropnest.ui.components.Tag
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.components.phIcon
import com.dropnest.ui.motion.rise
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TransfersScreen(wide: Boolean, motion: Boolean, opener: FileOpener, viewModel: TransfersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val platform = koinInject<PlatformServices>()
    val me = koinInject<com.dropnest.domain.DeviceIdentity>().info.collectAsStateWithLifecycle().value
    val t = N

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(if (wide) 24.dp else 14.dp, if (wide) 20.dp else 8.dp, if (wide) 24.dp else 14.dp, 22.dp), verticalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 11.dp)) {
        item {
            Column {
                Kicker("Live"); VSpace(if (wide) 5.dp else 3.dp); Heading("Transfers", if (wide) 26 else 24)
                if (wide) { VSpace(3.dp); Muted("Everything that moved in or out of this device", 12) }
            }
        }
        val live = state.live.filter { !it.status.isTerminal }
        val finished = state.live.filter { it.status.isTerminal } + state.history
        if (live.isEmpty() && finished.isEmpty()) item { EmptyNote("No transfers yet", "Fetch something from a nearby device and it shows up here.") }
        itemsIndexed(live, key = { _, s -> s.id }) { i, s -> LiveCard(s, wide, motion, me.deviceType, viewModel, Modifier.rise(i, s.id)) }
        if (finished.isNotEmpty()) item {
            Row(Modifier.padding(4.dp, 4.dp, 4.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Kicker(if (wide) "History" else "Earlier", accent = false, size = 11)
                Spacer(Modifier.weight(1f))
                NButton("Clear", { viewModel.clearFinished(); viewModel.clearHistory() }, style = NButtonStyle.Ghost, fontSize = 11, padding = PaddingValues(8.dp, 6.dp))
            }
        }
        itemsIndexed(finished, key = { _, s -> "h" + s.id }) { i, s -> HistoryCard(s, wide, opener, platform, viewModel, Modifier.rise(i, s.id)) }
    }
}

@Composable
private fun LiveCard(s: TransferSession, wide: Boolean, motion: Boolean, me: com.dropnest.model.DeviceType, vm: TransfersViewModel, modifier: Modifier) {
    val t = N
    val incoming = s.direction == Direction.RECEIVE
    val title = (if (incoming) "Receiving from " else "Sending to ") + s.peer.alias
    val sub = "${s.items.size} item${if (s.items.size == 1) "" else "s"} · ${formatBytes(s.bytesTotal)}" + if (s.speedBps > 0) " · ${formatSpeed(s.speedBps)} · ${formatEta(s.bytesTotal - s.bytesDone, s.speedBps)} left" else ""
    NCard(modifier.fillMaxWidth().animateContentSize(), radius = if (wide) 14.dp else 16.dp) {
        Box {
            Sheen(active = motion && s.status == SessionStatus.ACTIVE)
            Column(Modifier.padding(if (wide) 15.dp else 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (wide) AccentTile(if (incoming) Ph.ArrowDown else Ph.ArrowUp, size = 34.dp, iconSize = 17.dp)
                    else ProgressRing(s.progress)
                    HSpace(12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(title, color = t.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Muted(sub, 11)
                        if (!wide) { VSpace(8.dp); NButton("Cancel", { vm.cancel(s.id) }, style = NButtonStyle.Ghost, fontSize = 11, padding = PaddingValues(9.dp, 8.dp)) }
                    }
                    if (wide) Text("${(s.progress * 100).toInt()}%", color = t.accent, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                }
                VSpace(12.dp)
                val from = if (incoming) s.peer.deviceType.phIcon() else me.phIcon()
                val to = if (incoming) me.phIcon() else s.peer.deviceType.phIcon()
                Conveyor(from, to, active = motion && s.status == SessionStatus.ACTIVE, height = if (wide) 110.dp else 74.dp)
                if (wide) {
                    VSpace(12.dp)
                    Gauge(s.progress, Modifier.fillMaxWidth(), height = 6.dp)
                    VSpace(12.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NButton("Cancel", { vm.cancel(s.id) }, style = NButtonStyle.Ghost, fontSize = 11)
                        Spacer(Modifier.weight(1f))
                        Tag("LAN · encrypted")
                    }
                }
                if (s.status == SessionStatus.PENDING) { VSpace(8.dp); Muted("Waiting for ${s.peer.alias} to accept…", 11) }
            }
        }
    }
}

@Composable
private fun HistoryCard(s: TransferSession, wide: Boolean, opener: FileOpener, platform: PlatformServices, vm: TransfersViewModel, modifier: Modifier) {
    val t = N
    val ok = s.status == SessionStatus.COMPLETED
    NCard(modifier.fillMaxWidth(), radius = 12.dp, padding = PaddingValues(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTile(s.peer.deviceType.phIcon(), size = 36.dp, iconSize = 17.dp, muted = !ok)
            HSpace(11.dp)
            Column(Modifier.weight(1f)) {
                Text((if (s.direction == Direction.RECEIVE) "From " else "To ") + s.peer.alias, color = t.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Muted(statusLine(s), 11)
            }
            Icon(if (ok) Ph.Check else Ph.X, null, tint = if (ok) t.ok else t.bad, modifier = Modifier.size(16.dp))
            HSpace(4.dp)
            NIconButton(Ph.Trash, { vm.dismiss(s.id); vm.removeHistory(s.id) }, size = 30.dp, iconSize = 13.dp, style = NButtonStyle.Ghost, contentDescription = "Remove")
        }
        VSpace(4.dp)
        s.items.forEach { item ->
            val canOpen = s.direction == Direction.RECEIVE && item.status == ItemStatus.DONE && item.kind == ItemKind.FILE && item.resultPath != null
            ItemRow(
                name = if (item.kind == ItemKind.TEXT) item.content.orEmpty().lineSequence().first().take(80) else item.name,
                meta = item.error ?: if (item.kind == ItemKind.FILE) formatBytes(item.size) else if (item.kind == ItemKind.URL) "Link" else "Text",
                kind = item.kind, mimeType = item.mimeType, tileSize = 30.dp,
                previewModel = if (canOpen) platform.previewModel(item.resultPath!!) else null,
                onClick = when {
                    canOpen -> ({ opener.open(item.resultPath!!, item.name, item.mimeType) })
                    item.kind == ItemKind.URL && item.content != null -> ({ vm.openUrl(item.content) })
                    else -> null
                },
                modifier = Modifier.graphicsLayer { alpha = if (item.status == ItemStatus.FAILED || item.status == ItemStatus.SKIPPED) 0.55f else 1f },
            ) { if (canOpen) NButton("Open", { opener.open(item.resultPath!!, item.name, item.mimeType) }, style = NButtonStyle.Ghost, fontSize = 11, padding = PaddingValues(8.dp, 5.dp)) }
        }
    }
}

private fun statusLine(s: TransferSession): String = when (s.status) {
    SessionStatus.COMPLETED -> "Completed · ${formatBytes(s.bytesTotal)}"
    SessionStatus.COMPLETED_WITH_ERRORS -> "Completed with errors"
    SessionStatus.FAILED -> s.error ?: "Failed"
    SessionStatus.CANCELLED -> s.error ?: "Cancelled"
    SessionStatus.DECLINED -> s.error ?: "Declined"
    else -> "In progress"
}
