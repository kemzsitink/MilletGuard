package io.github.kemzsitink.milletguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        if (!SettingsGuard.isProtectionEnabled(context)) return

        val service = Intent(context, GuardService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                SettingsGuard.usePersistentNotification(context)
            ) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        } catch (_: Throwable) {
        }
    }
}
