package com.dropboxx.ui.box

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropboxx.core.formatBytes
import com.dropboxx.domain.PlatformServices
import com.dropboxx.model.ItemKind
import com.dropboxx.model.Peer
import com.dropboxx.ui.FileOpener
import com.dropboxx.ui.components.DeviceAvatar
import com.dropboxx.ui.components.EmptyState
import com.dropboxx.ui.components.SectionHeader
import com.dropboxx.ui.components.externalDropTarget
import com.dropboxx.ui.theme.AppIcons
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BoxScreen(wide: Boolean, opener: FileOpener, onMessage: (String) -> Unit, viewModel: BoxViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val platform = koinInject<PlatformServices>()
    val clipboard = LocalClipboardManager.current
    var textDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }

    val paste: () -> Unit = {
        val text = clipboard.getText()?.text
        if (text.isNullOrBlank()) textDialog = true else viewModel.addText(text)
    }
    val root = Modifier.fillMaxSize().externalDropTarget(onFiles = viewModel::addFiles, onText = viewModel::addText, onHover = viewModel::setDropHover)

    LazyColumn(root, contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { DropZone(state.dropHover, state.items.isNotEmpty(), state.importing, viewModel::pickFiles, paste) }
        item { Spacer(Modifier.height(6.dp)) }
        if (state.items.isEmpty()) {
            item { EmptyState(AppIcons.Inbox, "Your box is empty", "Files, photos, text and links you drop here wait for your other devices - no connection needed until then.") }
        } else {
            item {
                SectionHeader(
                    if (state.selected.isEmpty()) "${state.items.size} item${if (state.items.size == 1) "" else "s"} - ${formatBytes(state.totalBytes)}"
                    else "${state.selected.size} selected",
                ) {
                    if (state.selected.isNotEmpty()) TextButton(onClick = viewModel::clearSelection) { Text("Deselect") }
                    else TextButton(onClick = viewModel::clear) { Text("Clear box") }
                    SendMenu(state.peers, state.sendingTo, label = if (state.selected.isEmpty()) "Send all to" else "Send selected to") { viewModel.sendTo(it) }
                }
            }
            items(state.items, key = { it.id }) { item ->
                FileRow(
                    name = if (item.kind == ItemKind.TEXT) item.content.orEmpty() else item.name,
                    size = item.size, kind = item.kind, mimeType = item.mimeType,
                    previewModel = item.source?.let(platform::previewModel),
                    checked = item.id in state.selected, onCheckedChange = { viewModel.toggle(item.id) },
                    unavailable = !item.available,
                    onClick = {
                        when (item.kind) {
                            ItemKind.FILE -> item.source?.let { opener.open(it, item.name, item.mimeType) }
                            ItemKind.URL -> viewModel.open(item)
                            ItemKind.TEXT -> viewModel.toggle(item.id)
                        }
                    },
                    trailing = { RemoveButton { viewModel.remove(item.id) } },
                )
            }
        }
    }

    state.pinPrompt?.let { peer -> PinDialog(peer, onDismiss = viewModel::dismissPin) { pin -> viewModel.sendTo(peer, pin) } }
    if (textDialog) TextDialog(onDismiss = { textDialog = false }) { viewModel.addText(it); textDialog = false }
}

/** "Send to" button that drops down the list of nearby devices. */
@Composable
fun SendMenu(peers: List<Peer>, sendingTo: String?, label: String, onPick: (Peer) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Button(onClick = { open = true }, enabled = sendingTo == null) {
            if (sendingTo != null) CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
            else Icon(AppIcons.ArrowUp, contentDescription = null, modifier = Modifier.width(18.dp))
            Spacer(Modifier.width(6.dp)); Text(label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (peers.isEmpty()) DropdownMenuItem(text = { Text("No devices nearby") }, onClick = { open = false }, enabled = false)
            peers.forEach { peer ->
                DropdownMenuItem(
                    text = { Text(peer.info.alias) },
                    leadingIcon = { DeviceAvatar(peer.info.deviceType, size = 28, highlighted = peer.trusted) },
                    onClick = { open = false; onPick(peer) },
                )
            }
        }
    }
}

@Composable
fun PinDialog(peer: Peer, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN required") },
        text = {
            Column {
                Text("${peer.info.alias} asks for a PIN.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() } }, label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pin) }, enabled = pin.isNotEmpty()) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add text or link") },
        text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Paste a note or a URL") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
