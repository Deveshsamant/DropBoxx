package com.dropboxx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dropboxx.core.MimeTypes
import com.dropboxx.model.DeviceType
import com.dropboxx.model.ItemKind
import com.dropboxx.ui.theme.AppIcons

fun DeviceType.icon(): ImageVector = if (isMobile) AppIcons.Smartphone else AppIcons.Laptop

fun itemIcon(kind: ItemKind, mimeType: String): ImageVector = when {
    kind == ItemKind.URL -> AppIcons.Link
    kind == ItemKind.TEXT -> AppIcons.Notes
    MimeTypes.isImage(mimeType) -> AppIcons.Image
    MimeTypes.isVideo(mimeType) -> AppIcons.Video
    MimeTypes.isAudio(mimeType) -> AppIcons.Audio
    mimeType == "application/pdf" || mimeType.startsWith("text/") || mimeType.contains("document") -> AppIcons.Document
    else -> AppIcons.File
}

@Composable
fun DeviceAvatar(type: DeviceType, modifier: Modifier = Modifier, size: Int = 44, highlighted: Boolean = false) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val fg = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Box(modifier.size(size.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(type.icon(), contentDescription = null, tint = fg, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
