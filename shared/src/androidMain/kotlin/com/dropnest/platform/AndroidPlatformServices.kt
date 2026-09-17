package com.dropnest.platform

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import com.dropnest.core.MimeTypes
import com.dropnest.domain.BoxFileStore
import com.dropnest.domain.FilePicker
import com.dropnest.domain.HotspotController
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.ReceiveStorage
import com.dropnest.model.DeviceType
import com.dropnest.model.PlatformFile
import java.io.File

class AndroidPlatformServices(private val context: Context, private val launcherActivity: Class<*>) : PlatformServices {

    private val log = Logger.withTag("Android")
    private val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override val deviceType = DeviceType.ANDROID
    override val defaultAlias: String =
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.MODEL.ifBlank { "My phone" }
    override val dataDirectory: String = File(context.filesDir, "dropnest").apply { mkdirs() }.absolutePath
    override val defaultSaveDirectory: String = "Download/DropNest"

    override val receiveStorage: ReceiveStorage = AndroidReceiveStorage(context)
    override val boxFiles: BoxFileStore = AndroidBoxFileStore(context)
    override val hotspot: HotspotController = AndroidHotspotController(context)
    override val filePicker: FilePicker = object : FilePicker {
        override val canPickDirectory = false
        override suspend fun pickDirectory(): String? = null
        override suspend fun pickFiles(): List<PlatformFile> = ActivityBridge.pickFiles().map { AndroidFile(context, it) }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL_EVENTS, "Transfers", NotificationManager.IMPORTANCE_HIGH).apply { description = "Incoming requests and completed transfers" })
            notifications.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, "Background service", NotificationManager.IMPORTANCE_LOW).apply { description = "Shown while DropNest is ready to receive" })
        }
    }

    override fun notify(title: String, body: String) {
        val open = PendingIntent.getActivity(context, 0, Intent(context, launcherActivity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build()
        runCatching { notifications.notify((System.currentTimeMillis() % 100_000).toInt(), n) }
    }

    override fun openFile(pathOrUri: String) {
        val uri = toUri(pathOrUri) ?: run { toast("Cannot access this file"); return }
        val mime = context.contentResolver.getType(uri) ?: MimeTypes.fromFileName(pathOrUri.substringAfterLast('/'))
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Always go through the chooser so the user picks the viewer (PDF reader, video player...).
        val chooser = Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }.onFailure {
            log.w { "No app can open $uri ($mime)" }
            toast("No app on this phone can open ${mime.substringAfter('/')} files")
        }
    }

    private fun toast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show() }
    }

    override fun revealFile(pathOrUri: String) {
        runCatching { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { log.w { "Cannot open $url" } }
    }

    override fun previewModel(pathOrUri: String): Any? =
        if (pathOrUri.startsWith("content://")) Uri.parse(pathOrUri) else File(pathOrUri).takeIf { it.isFile }

    private fun toUri(pathOrUri: String): Uri? = when {
        pathOrUri.startsWith("content://") -> Uri.parse(pathOrUri)
        else -> runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", File(pathOrUri)) }.getOrNull()
    }

    override fun setMulticastEnabled(enabled: Boolean) {
        if (enabled) {
            if (multicastLock == null) multicastLock = wifi.createMulticastLock("dropnest").apply { setReferenceCounted(false); acquire() }
        } else {
            multicastLock?.let { if (it.isHeld) it.release() }
            multicastLock = null
        }
    }

    @Suppress("DEPRECATION")
    override fun setKeepAwake(enabled: Boolean) {
        if (enabled) {
            if (wakeLock == null) wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dropnest:transfer").apply { acquire(4 * 60 * 60 * 1000L) }
            if (wifiLock == null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifiLock = wifi.createWifiLock(mode, "dropnest:transfer").apply { acquire() }
            }
        } else {
            wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
            wifiLock?.let { if (it.isHeld) it.release() }; wifiLock = null
        }
    }

    override fun setLaunchAtStartup(enabled: Boolean) = false
    override val supportsTray = false

    companion object {
        const val CHANNEL_EVENTS = "transfers"
        const val CHANNEL_SERVICE = "service"
    }
}
