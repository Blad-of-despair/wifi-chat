package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class HomeLinkForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SHOW_INCOMING_CALL = "ACTION_SHOW_INCOMING_CALL"
        const val ACTION_ACCEPT_CALL = "ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "ACTION_DECLINE_CALL"
        const val ACTION_SHOW_BROADCAST = "ACTION_SHOW_BROADCAST"

        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_CALLER_NAME = "EXTRA_CALLER_NAME"
        const val EXTRA_IS_VIDEO = "EXTRA_IS_VIDEO"
        const val EXTRA_BROADCAST_TITLE = "EXTRA_BROADCAST_TITLE"
        const val EXTRA_BROADCAST_MSG = "EXTRA_BROADCAST_MSG"

        const val CHANNEL_STATUS_ID = "channel_homelink_status"
        const val CHANNEL_CALLS_ID = "channel_homelink_calls"
        const val CHANNEL_BROADCAST_ID = "channel_homelink_broadcast"

        const val NOTIF_STATUS_ID = 1001
        const val NOTIF_CALL_ID = 1002
        const val NOTIF_BROADCAST_ID = 1003

        fun start(context: Context) {
            val intent = Intent(context, HomeLinkForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HomeLinkForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_INCOMING_CALL -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Family Member"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                showIncomingCallNotification(callId, callerName, isVideo)
            }
            ACTION_SHOW_BROADCAST -> {
                val title = intent.getStringExtra(EXTRA_BROADCAST_TITLE) ?: "Family Announcement"
                val msg = intent.getStringExtra(EXTRA_BROADCAST_MSG) ?: ""
                showBroadcastNotification(title, msg)
            }
            else -> {
                startForegroundServiceNotification()
            }
        }
        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HomeLink::ForegroundWakeLock")?.apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours safe timeout
            }

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("HomeLink::MulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) {}
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Low priority persistent LAN status channel
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS_ID,
                "HomeLink Network Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps HomeLink alive on local Wi-Fi to receive messages and calls"
                setShowBadge(false)
            }

            // High priority incoming calls channel with sound & vibration
            val callSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val callChannel = NotificationChannel(
                CHANNEL_CALLS_ID,
                "HomeLink Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rings when family members call"
                setSound(
                    callSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000)
            }

            // High priority broadcast announcements
            val alertSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val broadcastChannel = NotificationChannel(
                CHANNEL_BROADCAST_ID,
                "HomeLink Family Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud announcements such as Dinner is Ready"
                setSound(
                    alertSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }

            manager?.createNotificationChannel(statusChannel)
            manager?.createNotificationChannel(callChannel)
            manager?.createNotificationChannel(broadcastChannel)
        }
    }

    private fun startForegroundServiceNotification() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_STATUS_ID)
            .setContentTitle("HomeLink LAN Active")
            .setContentText("Private local family network connected")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_STATUS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIF_STATUS_ID, notification)
        }
    }

    private fun showIncomingCallNotification(callId: String, callerName: String, isVideo: Boolean) {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_CALL", true)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_IS_VIDEO, isVideo)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 101, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeStr = if (isVideo) "Video Call" else "Voice Call"
        val notification = NotificationCompat.Builder(this, CHANNEL_CALLS_ID)
            .setContentTitle("Incoming $callTypeStr")
            .setContentText("$callerName is calling you on HomeLink")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIF_CALL_ID, notification)
    }

    private fun showBroadcastNotification(title: String, msg: String) {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 102, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_BROADCAST_ID)
            .setContentTitle(title)
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIF_BROADCAST_ID, notification)
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
