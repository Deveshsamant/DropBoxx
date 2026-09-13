package com.dropnest.ui.box

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dropnest.core.MimeTypes
import com.dropnest.core.formatBytes
import com.dropnest.model.ItemKind
import com.dropnest.ui.components.itemIcon
import com.dropnest.ui.theme.AppIcons

/** A row used by every file list: thumbnail/icon, name, size, optional checkbox and trailing action. */
@Composable
fun FileRow(
    name: String,
    size: Long,
    kind: ItemKind,
    mimeType: String,
    previewModel: Any?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    unavailable: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (kind == ItemKind.FILE && MimeTypes.isImage(mimeType) && previewModel != null) {
                AsyncImage(model = previewModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
            } else {
                Icon(itemIcon(kind, mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (kind == ItemKind.TEXT) name.lineSequence().first().take(80) else name,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                color = if (unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle ?: when {
                    unavailable -> "File is no longer available"
                    kind == ItemKind.FILE -> formatBytes(size)
                    kind == ItemKind.URL -> "Link"
                    else -> "Text"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Close, contentDescription = "Remove") }
}

@Composable
fun DropZone(hover: Boolean, hasItems: Boolean, importing: Boolean, onPick: () -> Unit, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    val border = if (hover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val bg = if (hover) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(if (hover) 2.dp else 1.dp, border),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AppIcons.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(if (hasItems) "Drop more into your box" else "Drop anything into your box", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (importing) "Adding..." else "It stays here until you remove it. Devices you allow can pick it up whenever they are nearby.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPick, enabled = !importing) {
                    Icon(AppIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add files")
                }
                OutlinedButton(onClick = onPaste, enabled = !importing) {
                    Icon(AppIcons.Paste, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Paste")
                }
            }
        }
    }
}
