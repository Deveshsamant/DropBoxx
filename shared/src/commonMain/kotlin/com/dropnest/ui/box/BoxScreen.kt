package com.dropnest.ui.box

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropnest.core.formatBytes
import com.dropnest.domain.PlatformServices
import com.dropnest.model.BoxItem
import com.dropnest.model.ItemKind
import com.dropnest.ui.FileOpener
import com.dropnest.ui.components.AccentTile
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
import com.dropnest.ui.components.NestChip
import com.dropnest.ui.components.NestHero
import com.dropnest.ui.components.SectionBar
import com.dropnest.ui.components.Tag
import com.dropnest.ui.components.TextDialog
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.components.Well
import com.dropnest.ui.components.externalDropTarget
import com.dropnest.ui.components.itemMeta
import com.dropnest.ui.motion.rememberSpin
import com.dropnest.ui.motion.rise
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BoxScreen(
    wide: Boolean,
    motion: Boolean,
    opener: FileOpener,
    onMessage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: BoxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val platform = koinInject<PlatformServices>()
    val clipboard = LocalClipboardManager.current
    var textDialog by remember { mutableStateOf(false) }
    val spin = rememberSpin(autoDegPerSec = 9.6f, enabled = motion)
    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }

    val paste: () -> Unit = {
        val text = clipboard.getText()?.text
        if (text.isNullOrBlank()) textDialog = true else viewModel.addText(text)
    }
    val chips = state.items.take(7).map { NestChip(it.id, it.kind, it.mimeType) }
    val openItem: (BoxItem) -> Unit = { item ->
        when (item.kind) {
            ItemKind.FILE -> item.source?.let { opener.open(it, item.name, item.mimeType) }
            ItemKind.URL -> viewModel.open(item)
            ItemKind.TEXT -> { clipboard.setText(AnnotatedString(item.content.orEmpty())); onMessage("Copied to clipboard") }
        }
    }
    val root = Modifier.fillMaxSize().externalDropTarget(onFiles = viewModel::addFiles, onText = viewModel::addText, onHover = viewModel::setDropHover)

    if (wide) DesktopBox(root, state, chips, spin, motion, viewModel, paste, openItem)
    else PhoneBox(root, state, chips, spin, motion, viewModel, paste, openItem, onOpenSettings)

    if (textDialog) TextDialog(onDismiss = { textDialog = false }) { viewModel.addText(it); textDialog = false }
}

@Composable
private fun DesktopBox(
    root: Modifier, state: BoxUiState, chips: List<NestChip>, spin: com.dropnest.ui.motion.SpinState, motion: Boolean,
    vm: BoxViewModel, paste: () -> Unit, open: (BoxItem) -> Unit,
) {
    val t = N
    val platform = koinInject<PlatformServices>()
    Column(root.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 22.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Kicker("Always yours")
                VSpace(5.dp)
                Heading("My box")
                VSpace(3.dp)
                Muted(boxMeta(state) + " · stays on this device", 12)
            }
            Spacer(Modifier.weight(1f))
            NButton("Paste", paste, icon = Ph.Clipboard)
            HSpace(8.dp)
            NButton("Add files", vm::pickFiles, icon = Ph.Plus, style = NButtonStyle.Primary, enabled = !state.importing)
        }
        VSpace(16.dp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NestWell(chips, spin, motion, state, Modifier.weight(1f), scale = 1.3f, label = "Your nest")
                DropTile(state.dropHover, state.importing, onClick = vm::pickFiles)
            }
            NCard(Modifier.weight(1.2f).fillMaxSize(), radius = 14.dp) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ph.Cube, null, tint = t.muted, modifier = Modifier.size(15.dp))
                    HSpace(9.dp)
                    Kicker("Contents", accent = false, size = 11)
                    Spacer(Modifier.weight(1f))
                    if (state.items.isNotEmpty()) NButton("Clear", vm::clear, style = NButtonStyle.Ghost, fontSize = 11, padding = PaddingValues(6.dp, 4.dp))
                    HSpace(6.dp)
                    Tag("Newest first")
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(t.soft))
                if (state.items.isEmpty()) {
                    EmptyNote("Your box is empty", "Drop files, photos, text and links into your nest. They wait here until a device you allow picks them up.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        itemsIndexed(state.items, key = { _, it -> it.id }) { i, item ->
                            ItemRow(
                                name = displayName(item), meta = itemMeta(item.kind, item.size) + (item.forPeerAlias?.let { " · only for $it" } ?: ""), kind = item.kind, mimeType = item.mimeType,
                                previewModel = item.source?.let(platform::previewModel), unavailable = !item.available, lift = true,
                                onClick = { open(item) }, modifier = Modifier.rise(i, item.id),
                            ) {
                                NIconButton(Ph.Trash, { vm.remove(item.id) }, size = 30.dp, iconSize = 14.dp, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneBox(
    root: Modifier, state: BoxUiState, chips: List<NestChip>, spin: com.dropnest.ui.motion.SpinState, motion: Boolean,
    vm: BoxViewModel, paste: () -> Unit, open: (BoxItem) -> Unit, onOpenSettings: () -> Unit,
) {
    val t = N
    val platform = koinInject<PlatformServices>()
    BoxWithConstraints(root) {
        // Short screens (landscape phones, split view) give the nest less room so the list stays usable.
        val short = maxHeight < 560.dp
        val nestHeight = (maxHeight * 0.34f).coerceIn(120.dp, 232.dp)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Kicker("Always yours")
                    VSpace(3.dp)
                    Heading("My box", 24)
                    VSpace(2.dp)
                    Muted(boxMeta(state), 11)
                }
                NIconButton(Ph.Gear, onOpenSettings, size = 44.dp, iconSize = 18.dp, contentDescription = "Settings")
            }
            Box(Modifier.fillMaxWidth().height(nestHeight)) {
                NestHero(chips, spin, Modifier.fillMaxSize(), scale = if (short) 0.8f else 0.92f, motion = motion)
                Row(Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Gauge(gaugeFraction(state), Modifier.weight(1f))
                    HSpace(8.dp)
                    Muted(formatBytes(state.totalBytes), 10)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                QuickChip("Files", Ph.File) { vm.pickFiles() }
                QuickChip("Photo", Ph.Image) { vm.pickFiles() }
                QuickChip("Text", Ph.TextLines) { vm.requestText() }
                QuickChip("Link", Ph.Link) { vm.requestText() }
                QuickChip("Clipboard", Ph.Clipboard, paste)
            }
            Well(Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 13.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Kicker("In your box", accent = false, size = 11)
                        Spacer(Modifier.weight(1f))
                        if (state.items.isNotEmpty()) NButton("Clear", vm::clear, style = NButtonStyle.Ghost, padding = PaddingValues(12.dp, 9.dp))
                    }
                    if (state.items.isEmpty()) {
                        EmptyNote("Nothing here yet", "Add files, share from any app, or paste a link. Everything waits for your other devices.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(state.items, key = { _, it -> it.id }) { i, item ->
                                ItemRow(
                                    name = displayName(item), meta = itemMeta(item.kind, item.size) + (item.forPeerAlias?.let { " · only for $it" } ?: ""), kind = item.kind, mimeType = item.mimeType,
                                    previewModel = item.source?.let(platform::previewModel), unavailable = !item.available, card = true, tileSize = 38.dp,
                                    onClick = { open(item) }, modifier = Modifier.rise(i, item.id),
                                ) {
                                    NIconButton(Ph.Trash, { vm.remove(item.id) }, size = 44.dp, iconSize = 17.dp, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
        // FAB (58 px, radius 19, accent-soft glass).
        Box(
            Modifier.align(Alignment.BottomEnd).padding(18.dp).size(58.dp).clip(RoundedCornerShape(19.dp))
                .background(t.accentSoft).border(1.dp, t.accentLine, RoundedCornerShape(19.dp)).clickable { vm.pickFiles() },
            contentAlignment = Alignment.Center,
        ) { Icon(Ph.Plus, "Add", tint = t.accent, modifier = Modifier.size(24.dp)) }
    }
    if (state.textPrompt) TextDialog(onDismiss = vm::dismissText) { vm.addText(it); vm.dismissText() }
}

@Composable
private fun QuickChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    NButton(label, onClick, icon = icon, fontSize = 11, padding = PaddingValues(horizontal = 11.dp, vertical = 12.dp))
}

@Composable
fun NestWell(chips: List<NestChip>, spin: com.dropnest.ui.motion.SpinState, motion: Boolean, state: BoxUiState, modifier: Modifier, scale: Float, label: String) {
    Well(modifier, radius = 14.dp, glow = true) {
        Box(Modifier.fillMaxSize()) {
            NestHero(chips, spin, Modifier.fillMaxSize(), scale = scale, motion = motion)
            Kicker(label, accent = false, modifier = Modifier.align(Alignment.TopStart).padding(12.dp, 11.dp))
            Row(Modifier.align(Alignment.BottomCenter).padding(12.dp, 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Gauge(gaugeFraction(state), Modifier.weight(1f))
                HSpace(8.dp)
                Muted(formatBytes(state.totalBytes), 10)
            }
        }
    }
}

@Composable
private fun DropTile(hover: Boolean, importing: Boolean, onClick: () -> Unit) {
    val t = N
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (hover) t.accentSoft.copy(alpha = t.accentSoft.alpha * 2) else t.accentSoft)
            .border(1.dp, if (hover) t.accent else t.accentLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentTile(Ph.ArrowDown, size = 34.dp, iconSize = 17.dp)
        HSpace(11.dp)
        Column {
            Text(if (importing) "Adding…" else if (hover) "Release to drop" else "Drop anything here", color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
            Muted("Files, folders, text, links — no peer needed", 11)
        }
    }
}

private fun boxMeta(state: BoxUiState): String =
    "${state.items.size} item${if (state.items.size == 1) "" else "s"} · ${formatBytes(state.totalBytes)}"

private fun gaugeFraction(state: BoxUiState): Float = (state.totalBytes / (2f * 1024 * 1024 * 1024)).coerceIn(0.02f, 1f)

private fun displayName(item: BoxItem): String = if (item.kind == ItemKind.TEXT) item.content.orEmpty().lineSequence().first().take(80) else item.name
