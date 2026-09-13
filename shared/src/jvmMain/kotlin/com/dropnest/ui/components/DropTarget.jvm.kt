package com.dropnest.ui.components

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import com.dropnest.model.PlatformFile
import com.dropnest.platform.expandFiles
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.externalDropTarget(
    onFiles: (List<PlatformFile>) -> Unit,
    onText: (String) -> Unit,
    onHover: (Boolean) -> Unit,
): Modifier {
    val target = remember(onFiles, onText, onHover) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = onHover(true)
            override fun onExited(event: DragAndDropEvent) = onHover(false)
            override fun onEnded(event: DragAndDropEvent) = onHover(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onHover(false)
                val t = event.awtTransferable
                return runCatching {
                    when {
                        t.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                            @Suppress("UNCHECKED_CAST")
                            val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                            onFiles(expandFiles(files)); true
                        }
                        t.isDataFlavorSupported(DataFlavor.stringFlavor) -> {
                            onText(t.getTransferData(DataFlavor.stringFlavor) as String); true
                        }
                        else -> false
                    }
                }.getOrDefault(false)
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}
