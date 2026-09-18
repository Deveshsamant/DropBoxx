package com.dropnest.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropnest.model.ChatMessage
import com.dropnest.model.Conversation
import com.dropnest.model.MessageStatus
import com.dropnest.model.Peer
import com.dropnest.model.looksLikeUrl
import com.dropnest.ui.components.AccentTile
import com.dropnest.ui.components.EmptyNote
import com.dropnest.ui.components.HSpace
import com.dropnest.ui.components.Heading
import com.dropnest.ui.components.Kicker
import com.dropnest.ui.components.Muted
import com.dropnest.ui.components.NButton
import com.dropnest.ui.components.NButtonStyle
import com.dropnest.ui.components.NCard
import com.dropnest.ui.components.NField
import com.dropnest.ui.components.NIconButton
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.components.Well
import com.dropnest.ui.components.phIcon
import com.dropnest.ui.motion.rise
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import com.dropnest.ui.FileOpener
import com.dropnest.ui.components.externalDropTarget
import com.dropnest.core.formatBytes
import com.dropnest.core.MimeTypes
import androidx.compose.material3.CircularProgressIndicator
import com.dropnest.core.localClock
import com.dropnest.core.nowMillis
import com.dropnest.LocalWindowWide
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Chats: every thread plus nearby trusted devices to start one. On wide layouts the selected
 * thread opens in a right-hand pane (like a desktop messenger); on phones it navigates.
 */
@Composable
fun ChatsScreen(
    wide: Boolean,
    motion: Boolean,
    opener: FileOpener,
    onOpenChat: (String) -> Unit,
    onOpenPeerBox: (Peer) -> Unit,
    onMessage: (String) -> Unit,
    viewModel: ChatsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val t = N

    val list: @Composable (Modifier) -> Unit = { modifier ->
        LazyColumn(modifier, contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.conversations.isEmpty() && state.newPeers.isEmpty() && state.untrusted.isEmpty()) item {
                EmptyNote("No one to message yet", "Chats work between devices that trust each other. Open a device's box and choose \"Always allow\" on both sides.")
            }
            itemsIndexed(state.conversations, key = { _, c -> c.peer.id }) { i, c ->
                ThreadRow(c, active = wide && selected == c.peer.id, Modifier.rise(i, c.peer.id)) { if (wide) selected = c.peer.id else onOpenChat(c.peer.id) }
            }
            if (state.newPeers.isNotEmpty()) {
                item { Box(Modifier.padding(6.dp, 12.dp, 6.dp, 4.dp)) { Kicker("Nearby · trusted", accent = false, size = 10) } }
                itemsIndexed(state.newPeers, key = { _, p -> "n" + p.id }) { i, p ->
                    PeerRow(p, "Tap to start a chat", Modifier.rise(i, p.id)) { if (wide) selected = p.id else onOpenChat(p.id) }
                }
            }
            if (state.untrusted.isNotEmpty()) {
                item { Box(Modifier.padding(6.dp, 12.dp, 6.dp, 4.dp)) { Kicker("Nearby · not trusted yet", accent = false, size = 10) } }
                itemsIndexed(state.untrusted, key = { _, p -> "u" + p.id }) { i, p ->
                    PeerRow(p, "Open their box and ask for \"Always allow\" to chat", Modifier.rise(i, p.id), muted = true) { onOpenPeerBox(p) }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().padding(24.dp, 20.dp, 24.dp, 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(if (LocalWindowWide.current) 320.dp else 260.dp).fillMaxHeight()) {
                Kicker("Between your devices"); VSpace(5.dp); Heading("Chats"); VSpace(3.dp)
                Muted("Write any time - delivered when the device is nearby", 12)
                VSpace(14.dp)
                list(Modifier.fillMaxSize())
            }
            Well(Modifier.weight(1f).fillMaxHeight(), radius = 16.dp) {
                val id = selected
                if (id == null) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    AccentTile(Ph.ChatCircle, size = 56.dp, iconSize = 28.dp, radius = 16.dp, muted = true); VSpace(12.dp)
                    Muted("Pick a device on the left", 13)
                } else ChatScreen(id, wide = true, embedded = true, opener = opener, onBack = { selected = null }, onMessage = onMessage)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(14.dp, 8.dp, 14.dp, 0.dp)) {
            Kicker("Between your devices"); VSpace(3.dp); Heading("Chats", 24)
            VSpace(10.dp)
            list(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ThreadRow(c: Conversation, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = N
    NCard(modifier.fillMaxWidth(), radius = 12.dp, color = if (active) t.accentSoft else t.surface, onClick = onClick, padding = PaddingValues(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                AccentTile(c.peer.deviceType.phIcon(), size = 40.dp, iconSize = 20.dp, radius = 11.dp)
                if (c.online) Box(Modifier.align(Alignment.BottomEnd).size(11.dp).clip(CircleShape).background(t.surface).padding(2.dp).clip(CircleShape).background(t.ok))
            }
            HSpace(11.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.peer.alias, color = t.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    c.lastMessage?.let { Muted(shortTime(it.sentAt), 10) }
                }
                val preview = c.lastMessage?.let { (if (it.fromMe) "You: " else "") + it.text } ?: if (c.online) "Online · say hi" else "No messages yet"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Muted(preview, 11, Modifier.weight(1f), maxLines = 1)
                    if (c.pending > 0) { HSpace(6.dp); Icon(Ph.Clock, null, tint = t.muted, modifier = Modifier.size(12.dp)) }
                    if (c.unread > 0) { HSpace(6.dp); Text("${c.unread}", color = t.bg, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(t.accent).padding(6.dp, 1.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(p: Peer, hint: String, modifier: Modifier, muted: Boolean = false, onClick: () -> Unit) {
    val t = N
    NCard(modifier.fillMaxWidth(), radius = 12.dp, color = t.surface, onClick = onClick, padding = PaddingValues(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTile(p.info.deviceType.phIcon(), size = 40.dp, iconSize = 20.dp, radius = 11.dp, muted = muted)
            HSpace(11.dp)
            Column(Modifier.weight(1f)) {
                Text(p.info.alias, color = t.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(hint, 11)
            }
            Icon(Ph.CaretRight, null, tint = t.muted, modifier = Modifier.size(14.dp))
        }
    }
}

/** One thread: bubbles, delivery ticks, composer. [embedded] = inside the desktop two-pane layout. */
@Composable
fun ChatScreen(
    peerId: String,
    wide: Boolean,
    embedded: Boolean = false,
    opener: FileOpener,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: ChatViewModel = koinViewModel(key = "chat-$peerId") { parametersOf(peerId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fetching by viewModel.fetching.collectAsStateWithLifecycle()
    var dropHover by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }
    val t = N
    var draft by rememberSaveable(peerId) { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(viewModel) { viewModel.opened(); onDispose { viewModel.closed() } }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) { listState.animateScrollToItem(state.messages.size - 1); viewModel.seen() }
    }
    fun send() { val text = draft.trim(); if (text.isEmpty()) return; viewModel.send(text); draft = "" }

    Column(
        Modifier.fillMaxSize()
            .externalDropTarget(onFiles = viewModel::sendFiles, onText = { viewModel.send(it) }, onHover = { dropHover = it })
            .then(if (dropHover) Modifier.background(t.accentSoft) else Modifier),
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(if (embedded) 12.dp else 14.dp, if (embedded) 10.dp else 6.dp, 12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) { NIconButton(Ph.CaretLeft, onBack, size = if (wide) 32.dp else 40.dp, iconSize = 16.dp, contentDescription = "Back"); HSpace(8.dp) }
            state.peer?.let { AccentTile(it.deviceType.phIcon(), size = 36.dp, iconSize = 18.dp, radius = 10.dp); HSpace(10.dp) }
            Column(Modifier.weight(1f)) {
                Text(state.peer?.alias ?: "Device", color = t.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.online) t.ok else t.muted)); HSpace(5.dp)
                    Muted(if (state.online) "Online" else "Offline · messages are delivered when it's nearby", 11, maxLines = 1)
                }
            }
            if (state.messages.isNotEmpty()) NButton("Clear", viewModel::clear, style = NButtonStyle.Ghost, fontSize = 11)
        }
        if (!state.canMessage) {
            Row(Modifier.padding(14.dp, 0.dp, 14.dp, 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.accentSoft).padding(11.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ph.ShieldCheck, null, tint = t.accent, modifier = Modifier.size(16.dp)); HSpace(9.dp)
                Muted("${state.peer?.alias ?: "This device"} hasn't trusted you yet. Open their box and ask them to choose \"Always allow\"; then messages go through.", 11)
            }
        }
        // Messages
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AccentTile(Ph.ChatCircle, size = 52.dp, iconSize = 26.dp, radius = 15.dp, muted = true); VSpace(10.dp)
                    Muted("Say hi. Messages stay on your devices and travel over your own Wi-Fi.", 12)
                }
            }
            itemsIndexed(state.messages, key = { _, m -> m.id }) { i, m ->
                val prev = state.messages.getOrNull(i - 1)
                if (prev == null || dayOf(prev.sentAt) != dayOf(m.sentAt)) DayDivider(m.sentAt)
                Bubble(m, wide, fetching = m.id in fetching, onRetry = { viewModel.retry(m.id) }, onOpenUrl = viewModel::openUrl,
                    onFetch = { viewModel.fetch(m) }, onOpen = { m.localPath?.let { p -> opener.open(p, m.attachment?.name ?: m.text, m.attachment?.mimeType ?: "*/*") } })
            }
        }
        // Composer
        Row(Modifier.fillMaxWidth().padding(if (embedded) 12.dp else 14.dp, 6.dp, 12.dp, if (embedded) 12.dp else 10.dp), verticalAlignment = Alignment.Bottom) {
            NIconButton(Ph.Paperclip, viewModel::pickFiles, size = 44.dp, iconSize = 20.dp, contentDescription = "Send a file (only ${state.peer?.alias ?: "this device"} can fetch it)")
            HSpace(6.dp)
            NField(draft, { draft = it }, Modifier.weight(1f), placeholder = "Message", singleLine = false, minHeight = 44.dp)
            HSpace(8.dp)
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (draft.isBlank()) t.soft else t.accent).clickable(enabled = draft.isNotBlank()) { send() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ph.PaperPlane, "Send", tint = if (draft.isBlank()) t.muted else t.bg, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, wide: Boolean, fetching: Boolean, onRetry: () -> Unit, onOpenUrl: (String) -> Unit, onFetch: () -> Unit, onOpen: () -> Unit) {
    val t = N
    val mine = m.fromMe
    val att = m.attachment
    val url = if (att == null) m.text.trim().takeIf { it.looksLikeUrl() } else null
    val shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (mine) 14.dp else 4.dp, bottomEnd = if (mine) 4.dp else 14.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier.fillMaxWidth(if (wide) 0.7f else 0.84f).wrapContentWidth(if (mine) Alignment.End else Alignment.Start)
                .clip(shape).background(if (mine) t.accent else t.surface).border(1.dp, if (mine) t.accent else t.edge, shape)
                .then(
                    when {
                        url != null -> Modifier.clickable { onOpenUrl(url) }
                        att != null && !mine && m.localPath != null -> Modifier.clickable(onClick = onOpen)
                        att != null && !mine && !fetching -> Modifier.clickable(onClick = onFetch)
                        m.status == MessageStatus.FAILED -> Modifier.clickable(onClick = onRetry)
                        else -> Modifier
                    },
                )
                .padding(11.dp, 7.dp, 11.dp, 5.dp),
        ) {
            if (att != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(if (mine) t.bg.copy(alpha = 0.18f) else t.accentSoft), contentAlignment = Alignment.Center) {
                        Icon(if (MimeTypes.isImage(att.mimeType)) Ph.Image else Ph.File, null, tint = if (mine) t.bg else t.accent, modifier = Modifier.size(18.dp))
                    }
                    HSpace(9.dp)
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(att.name, color = if (mine) t.bg else t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            formatBytes(att.size) + when {
                                mine -> " · only for this device"
                                m.localPath != null -> " · saved · tap to open"
                                fetching -> " · fetching…"
                                else -> " · tap to fetch"
                            },
                            color = if (mine) t.bg.copy(alpha = 0.75f) else t.muted, fontSize = 10.5.sp,
                        )
                    }
                    if (!mine) {
                        HSpace(8.dp)
                        if (fetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = t.accent)
                        else Icon(if (m.localPath != null) Ph.Check else Ph.ArrowDown, null, tint = t.accent, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Text(m.text, color = if (mine) t.bg else t.text, fontSize = 13.5.sp, lineHeight = 18.sp, textDecoration = if (url != null) androidx.compose.ui.text.style.TextDecoration.Underline else null)
            }
            Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(shortTime(m.sentAt), color = if (mine) t.bg.copy(alpha = 0.7f) else t.muted, fontSize = 9.5.sp)
                if (mine) {
                    HSpace(4.dp)
                    when (m.status) {
                        MessageStatus.PENDING -> Icon(Ph.Clock, "Waiting", tint = t.bg.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                        MessageStatus.SENT -> Icon(Ph.Checks, "Delivered", tint = t.bg, modifier = Modifier.size(13.dp))
                        MessageStatus.FAILED -> Icon(Ph.X, "Not delivered", tint = t.bad, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
        if (m.status == MessageStatus.FAILED && m.error != null) Muted("${m.error} · tap to retry", 10, Modifier.padding(top = 2.dp, end = 4.dp))
    }
}

@Composable
private fun DayDivider(at: Long) {
    val t = N
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        Text(dayLabel(at), color = t.muted, fontSize = 10.sp, modifier = Modifier.clip(CircleShape).background(t.bg2).padding(10.dp, 3.dp))
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private fun dayOf(millis: Long) = localClock(millis).epochDay
private fun dayLabel(millis: Long): String {
    val d = localClock(millis)
    val today = localClock(nowMillis()).epochDay
    return when (d.epochDay) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> "${d.day} ${MONTHS[d.month - 1]} ${d.year}"
    }
}
private fun shortTime(millis: Long): String {
    val t = localClock(millis)
    val h = t.hour % 12; val hh = if (h == 0) 12 else h
    return "$hh:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "am" else "pm"}"
}
