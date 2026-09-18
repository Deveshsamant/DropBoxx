package com.dropnest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dropnest.core.MimeTypes
import com.dropnest.core.formatBytes
import com.dropnest.model.ItemKind
import com.dropnest.model.Peer
import com.dropnest.ui.motion.hoverLift
import com.dropnest.ui.theme.N

/** One line of any item list: accent tile (or thumbnail), name, meta, trailing actions. */
@Composable
fun ItemRow(
    name: String,
    meta: String,
    kind: ItemKind,
    mimeType: String,
    modifier: Modifier = Modifier,
    previewModel: Any? = null,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onChecked: ((Boolean) -> Unit)? = null,
    unavailable: Boolean = false,
    card: Boolean = false,
    lift: Boolean = false,
    tileSize: Dp = 36.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = N
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (card) 12.dp else 10.dp)
    Row(
        modifier.fillMaxWidth()
            .then(if (lift) Modifier.hoverLift(interaction) else Modifier)
            .clip(shape)
            .then(if (card) Modifier.background(t.surface).border(1.dp, t.edge, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = ripple(color = t.accent), onClick = onClick) else Modifier)
            .padding(if (card) 11.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            Checkbox(checked, onChecked, colors = CheckboxDefaults.colors(checkedColor = t.accent, uncheckedColor = t.muted, checkmarkColor = t.bg), modifier = Modifier.size(28.dp))
            HSpace(8.dp)
        }
        if (kind == ItemKind.FILE && MimeTypes.isImage(mimeType) && previewModel != null) {
            Box(Modifier.size(tileSize).clip(RoundedCornerShape(9.dp)).border(1.dp, t.accentLine, RoundedCornerShape(9.dp))) {
                AsyncImage(model = previewModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(tileSize))
            }
        } else {
            AccentTile(nestIcon(kind, mimeType), size = tileSize, iconSize = tileSize / 2)
        }
        HSpace(11.dp)
        Column(Modifier.weight(1f)) {
            Text(
                name, color = if (unavailable) t.bad else t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(if (unavailable) "File is no longer available" else meta, color = if (unavailable) t.bad else t.muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}

fun itemMeta(kind: ItemKind, size: Long): String = when (kind) {
    ItemKind.FILE -> formatBytes(size)
    ItemKind.URL -> "Link"
    ItemKind.TEXT -> "Text"
}

/** Header row for a section: kicker on the left, actions on the right. */
@Composable
fun SectionBar(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Kicker(title, accent = false, size = 11)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = N.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
        VSpace(4.dp)
        Text(body, color = N.muted, fontSize = 12.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Nocturne-styled Material dialog shell. */
@Composable
fun NDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissText: String = "Cancel",
    content: @Composable () -> Unit,
) {
    val t = N
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        titleContentColor = t.text,
        textContentColor = t.text,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = { content() },
        confirmButton = { NButton(confirmText, onConfirm, style = NButtonStyle.Primary, enabled = confirmEnabled) },
        dismissButton = { NButton(dismissText, onDismiss) },
    )
}

@Composable
fun TextDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    NDialog("Add text or link", onDismiss, "Add", { onConfirm(text) }, confirmEnabled = text.isNotBlank()) {
        NField(text, { text = it }, placeholder = "Paste a note or a URL", singleLine = false, minHeight = 96.dp)
    }
}

@Composable
fun PinDialog(peer: Peer, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    NDialog("PIN required", onDismiss, "Continue", { onConfirm(pin) }, confirmEnabled = pin.isNotEmpty()) {
        Column {
            Muted("${peer.info.alias} asks for a PIN.", 12)
            VSpace(10.dp)
            NField(pin, { pin = it.filter { c -> c.isDigit() } }, label = "PIN", numeric = true, password = true)
        }
    }
}

@Composable
fun AddressDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var address by remember { mutableStateOf("") }
    NDialog("Connect by IP address", onDismiss, "Connect", { onConfirm(address) }, confirmEnabled = address.isNotBlank()) {
        Column {
            Muted("Shown on the other device under Devices, e.g. 192.168.1.20 or 192.168.1.20:47843.", 12)
            VSpace(10.dp)
            NField(address, { address = it.trim() }, label = "IP address")
        }
    }
}
