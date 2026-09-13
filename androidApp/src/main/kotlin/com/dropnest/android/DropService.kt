package com.dropnest.android

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dropnest.domain.LocalServer
import com.dropnest.domain.ServerState
import com.dropnest.platform.AndroidPlatformServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the process (and the HTTPS server) alive while the app is in the background so other
 * devices can still send to this phone. Stopped from the notification or when the user quits.
 */
class DropService : Service() {

    private val server: LocalServer by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            server.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(buildNotification("Starting..."))
        server.start()
        if (stateJob == null) stateJob = scope.launch {
            server.state.collect { s ->
                val text = when (s) {
                    is ServerState.Running -> "Ready to receive on ${s.addresses.firstOrNull() ?: "this network"}"
                    ServerState.Starting -> "Starting..."
                    ServerState.Stopped -> "Receiving is off"
                    is ServerState.Failed -> s.message
                }
                runCatching { startInForeground(buildNotification(text)) }
            }
        }
        return START_STICKY
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, DropService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, AndroidPlatformServices.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DropNest").setContentText(text)
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stop)
            .build()
    }

    /**
     * Android 15+ caps dataSync services at 6 h per day and calls this when time is up; the app
     * must stop promptly or it is killed. The server keeps running while the activity is visible.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.dropnest.action.STOP"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DropService::class.java))
        }
    }
}
