package com.dropboxx.ui.transfers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropboxx.core.formatBytes
import com.dropboxx.core.formatEta
import com.dropboxx.core.formatSpeed
import com.dropboxx.model.Direction
import com.dropboxx.model.ItemKind
import com.dropboxx.model.ItemStatus
import com.dropboxx.model.SessionStatus
import com.dropboxx.model.TransferItem
import com.dropboxx.model.TransferSession
import com.dropboxx.domain.PlatformServices
import com.dropboxx.ui.FileOpener
import com.dropboxx.ui.components.DeviceAvatar
import org.koin.compose.koinInject
import com.dropboxx.ui.components.EmptyState
import com.dropboxx.ui.components.SectionHeader
import com.dropboxx.ui.components.itemIcon
import com.dropboxx.ui.theme.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TransfersScreen(opener: FileOpener, viewModel: TransfersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val platform = koinInject<PlatformServices>()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.live.isNotEmpty()) {
            item { SectionHeader("Active") { TextButton(onClick = viewModel::clearFinished) { Text("Clear finished") } } }
            items(state.live, key = { it.id }) { session ->
                SessionCard(session, live = true, viewModel, opener, platform)
            }
        }
        item { SectionHeader("History") { if (state.history.isNotEmpty()) TextButton(onClick = viewModel::clearHistory) { Text("Clear") } } }
        if (state.history.isEmpty() && state.live.isEmpty()) {
            item { EmptyState(AppIcons.History, "No transfers yet", "Everything you send or receive shows up here.") }
        }
        items(state.history, key = { "h-" + it.id }) { session ->
            SessionCard(session, live = false, viewModel, opener, platform)
        }
    }
}

@Composable
private fun SessionCard(s: TransferSession, live: Boolean, vm: TransfersViewModel, opener: FileOpener, platform: PlatformServices) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(s.peer.deviceType, size = 36)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text((if (s.direction == Direction.SEND) "To " else "From ") + s.peer.alias, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(statusLine(s), style = MaterialTheme.typography.bodySmall, color = statusColor(s.status))
                }
                Icon(if (s.direction == Direction.SEND) AppIcons.ArrowUp else AppIcons.ArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (live) IconButton(onClick = { if (s.status.isTerminal) vm.dismiss(s.id) else vm.cancel(s.id) }) {
                    Icon(Icons.Default.Close, contentDescription = if (s.status.isTerminal) "Dismiss" else "Cancel")
                }
                else IconButton(onClick = { vm.removeHistory(s.id) }) { Icon(Icons.Default.Close, contentDescription = "Remove") }
            }
            if (s.status == SessionStatus.ACTIVE && s.bytesTotal > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("${formatBytes(s.bytesDone)} / ${formatBytes(s.bytesTotal)}  -  ${formatSpeed(s.speedBps)}  -  ${formatEta(s.bytesTotal - s.bytesDone, s.speedBps)} left",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            s.items.forEach { item -> ItemRow(item, s, vm, opener, platform) }
        }
    }
}

@Composable
private fun ItemRow(item: TransferItem, s: TransferSession, vm: TransfersViewModel, opener: FileOpener, platform: PlatformServices) {
    val canOpen = s.direction == Direction.RECEIVE && item.status == ItemStatus.DONE && item.kind == ItemKind.FILE && item.resultPath != null
    val open: () -> Unit = {
        when {
            canOpen -> opener.open(item.resultPath.orEmpty(), item.name, item.mimeType)
            item.kind == ItemKind.URL && item.content != null -> vm.openUrl(item.content)
        }
    }
    val preview = if (canOpen) platform.previewModel(item.resultPath.orEmpty()) else null
    Row(
        Modifier.fillMaxWidth().then(if (canOpen || item.kind == ItemKind.URL) Modifier.clickable(onClick = open) else Modifier).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (preview != null && com.dropboxx.core.MimeTypes.isImage(item.mimeType)) {
            coil3.compose.AsyncImage(model = preview, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)))
        } else Icon(itemIcon(item.kind, item.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(if (item.kind == ItemKind.FILE) item.name else item.content.orEmpty().lineSequence().first().take(100), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            val detail = when {
                item.error != null -> item.error
                item.kind != ItemKind.FILE -> if (item.kind == ItemKind.URL) "Link" else "Text"
                item.status == ItemStatus.ACTIVE -> "${formatBytes(item.bytesDone)} / ${formatBytes(item.size)}"
                else -> formatBytes(item.size)
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = if (item.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canOpen || (item.kind == ItemKind.URL && item.content != null)) {
            TextButton(onClick = open) { Text("Open") }
        }
    }
}

private fun statusLine(s: TransferSession): String = when (s.status) {
    SessionStatus.PENDING -> "Waiting for ${s.peer.alias} to accept"
    SessionStatus.ACTIVE -> "Transferring ${s.items.size} item${if (s.items.size == 1) "" else "s"}"
    SessionStatus.COMPLETED -> "Completed - ${formatBytes(s.bytesTotal)}"
    SessionStatus.COMPLETED_WITH_ERRORS -> "Completed with errors"
    SessionStatus.FAILED -> s.error ?: "Failed"
    SessionStatus.CANCELLED -> s.error ?: "Cancelled"
    SessionStatus.DECLINED -> s.error ?: "Declined"
}

@Composable
private fun statusColor(status: SessionStatus) = when (status) {
    SessionStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
    SessionStatus.FAILED, SessionStatus.DECLINED, SessionStatus.COMPLETED_WITH_ERRORS -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
