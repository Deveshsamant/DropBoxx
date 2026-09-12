package com.dropboxx.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dropboxx.core.MimeTypes
import com.dropboxx.domain.PlatformServices
import com.dropboxx.ui.components.ImageViewerDialog

data class OpenRequest(val pathOrUri: String, val name: String, val mimeType: String)

/**
 * One tap-to-open policy for every list in the app: images show in the in-app viewer,
 * everything else (PDF, video, documents...) goes to the system's default app.
 */
class FileOpener(private val platform: PlatformServices) {
    var viewing by mutableStateOf<OpenRequest?>(null)
        private set

    fun open(pathOrUri: String, name: String, mimeType: String) {
        val model = platform.previewModel(pathOrUri)
        if (MimeTypes.isImage(mimeType) && model != null) viewing = OpenRequest(pathOrUri, name, mimeType)
        else platform.openFile(pathOrUri)
    }

    fun openExternal() { viewing?.let { platform.openFile(it.pathOrUri) } }
    fun dismiss() { viewing = null }
}

@Composable
fun rememberFileOpener(platform: PlatformServices): FileOpener = remember(platform) { FileOpener(platform) }

@Composable
fun FileOpenerHost(opener: FileOpener, platform: PlatformServices) {
    opener.viewing?.let { req ->
        val model = platform.previewModel(req.pathOrUri) ?: return
        ImageViewerDialog(model, req.name, onOpenExternal = opener::openExternal, onDismiss = opener::dismiss)
    }
}
