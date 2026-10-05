package io.github.kemzsitink.milletguard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.content.getSystemService

class GuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private var foreground = false

    private val fallbackCheck = object : Runnable {
        override fun run() {
            repair(false)
            handler.postDelayed(this, FALLBACK_INTERVAL_MS)
        }
    }

    private val repairDebounced = Runnable { repair(true) }

    override fun onCreate() {
        super.onCreate()
        applyExecutionMode()
        registerObserver()
        SettingsGuard.rememberIfUseful(this)
        handler.post(fallbackCheck)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SettingsGuard.setProtectionEnabled(this, true)
        registerObserver()
        applyExecutionMode()
        handler.removeCallbacks(fallbackCheck)
        handler.post(fallbackCheck)
        return START_STICKY
    }

    private fun repair(notifyFailure: Boolean) {
        val result = SettingsGuard.repair(this)
        if (result.changed) {
            FcmReconnect.kick(this)
            if (foreground) refreshNotification(getString(R.string.notification_repaired))
        } else if (!result.success && notifyFailure && foreground) {
            refreshNotification(result.message)
        }
    }

    private fun registerObserver() {
        val resolver = contentResolver
        try {
            observer?.let { resolver.unregisterContentObserver(it) }
        } catch (_: Throwable) {
        }

        val newObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                handler.removeCallbacks(repairDebounced)
                handler.postDelayed(repairDebounced, 400L)
            }
        }
        observer = newObserver
        resolver.registerContentObserver(
            Settings.System.getUriFor(SettingsGuard.getConfiguredKey(this)),
            false,
            newObserver,
        )
    }

    @Suppress("DEPRECATION")
    private fun applyExecutionMode() {
        if (SettingsGuard.usePersistentNotification(this)) {
            ensureNotificationChannel(this)
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_active)))
            foreground = true
        } else {
            if (foreground) stopForeground(true)
            getSystemService<NotificationManager>()?.cancel(NOTIFICATION_ID)
            foreground = false
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            observer?.let { contentResolver.unregisterContentObserver(it) }
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshNotification(text: String?) {
        if (!foreground) return
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(text: String?): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }

        return builder
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pi)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 426
        private const val FALLBACK_INTERVAL_MS = 30L * 60L * 1000L

        /**
         * Creates the visible-but-silent foreground-service channel. Returns true only
         * when this call created the channel for the first time.
         */
        fun ensureNotificationChannel(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val nm = context.getSystemService<NotificationManager>() ?: return false

            val created = nm.getNotificationChannel(CHANNEL_ID) == null
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            nm.createNotificationChannel(channel)
            return created
        }

        /** True when Android will actually place the foreground notification in the shade. */
        fun canShowPersistentNotification(context: Context): Boolean {
            val nm = context.getSystemService<NotificationManager>() ?: return false

            if (!nm.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = nm.getNotificationChannel(CHANNEL_ID)
                return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
            }
            return true
        }
    }
}
