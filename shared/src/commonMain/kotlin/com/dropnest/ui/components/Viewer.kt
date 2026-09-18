package com.dropnest.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.dropnest.ui.motion.dialogEnter
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph

/** Full-screen in-app image viewer; anything else is handed to the system's app via "Open with". */
@Composable
fun ImageViewerDialog(model: Any, title: String, onOpenExternal: () -> Unit, onDismiss: () -> Unit) {
    val t = N
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        Box(Modifier.fillMaxSize().background(Color(0xFF07080D).copy(alpha = 0.97f))) {
            AnimatedVisibility(shown, enter = dialogEnter, modifier = Modifier.fillMaxSize()) {
                AsyncImage(model = model, contentDescription = title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp, 56.dp, 10.dp, 64.dp))
            }
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 10.dp))
                NIconButton(Ph.X, onDismiss, size = 36.dp, iconSize = 16.dp, contentDescription = "Close")
            }
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(14.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                NButton("Open with…", onOpenExternal, style = NButtonStyle.Primary, icon = Ph.ArrowUp, fontSize = 12)
            }
        }
    }
}
