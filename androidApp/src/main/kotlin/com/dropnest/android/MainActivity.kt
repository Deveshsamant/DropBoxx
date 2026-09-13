package com.dropnest.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import com.dropnest.App
import com.dropnest.core.randomId
import com.dropnest.model.OutgoingItem
import com.dropnest.model.looksLikeUrl
import com.dropnest.platform.ActivityBridge
import com.dropnest.platform.AndroidFile
import com.dropnest.ui.ShareInbox
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity(), ActivityBridge.Host {

    private val inbox: ShareInbox by inject()

    // OpenMultipleDocuments grants persistable read access, so box items keep working after a restart.
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        ActivityBridge.onFilesPicked(uris)
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        ActivityBridge.onPermissionsResult(result)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ActivityBridge.attach(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        DropService.start(this)
        handleShare(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onDestroy() {
        ActivityBridge.detach(this)
        super.onDestroy()
    }

    override fun launchFilePicker() = filePicker.launch(arrayOf("*/*"))
    override fun launchPermissions(permissions: Array<String>) = this.permissions.launch(permissions)

    /** Share sheet entry point: ACTION_SEND / ACTION_SEND_MULTIPLE with streams or text. */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val items = mutableListOf<OutgoingItem>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { items += OutgoingItem.File(randomId(8), AndroidFile(this, it)) }
                // WhatsApp / Instagram / browsers share links as EXTRA_TEXT (sometimes with a caption or subject).
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (text.isNotEmpty() && items.isEmpty()) {
                    val url = text.split(Regex("""\s+""")).firstOrNull { it.looksLikeUrl() }
                    items += when {
                        text.looksLikeUrl() -> OutgoingItem.Url(randomId(8), text)
                        url != null && text.length - url.length < 200 -> OutgoingItem.Url(randomId(8), url)
                        else -> OutgoingItem.Text(randomId(8), listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), text).joinToString("\n"))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.forEach { items += OutgoingItem.File(randomId(8), AndroidFile(this, it)) }
            }
            Intent.ACTION_VIEW -> intent.data?.let { items += OutgoingItem.File(randomId(8), AndroidFile(this, it)) }
        }
        if (items.isNotEmpty()) {
            inbox.offer(items)
            intent.action = null
        }
    }
}
