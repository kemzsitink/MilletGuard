package io.github.kemzsitink.milletguard

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Shared protection toggle used by both the dashboard switch and the Quick Settings
 * tile, so both entry points run exactly the same enable/disable sequence.
 *
 * Configuration (settings key and required item) must already be saved by the caller;
 * this object only owns the runtime transition.
 */
object ProtectionController {

    fun enable(context: Context): Result {
        if (!SettingsGuard.canWrite(context)) {
            return Result(false, context.getString(R.string.permission_missing))
        }

        SettingsGuard.setProtectionEnabled(context, true)
        if (!startService(context)) {
            SettingsGuard.setProtectionEnabled(context, false)
            return Result(false, context.getString(R.string.service_stopped))
        }

        val repair = SettingsGuard.repair(context)
        if (repair.requiredRestored) FcmReconnect.recover(context)
        return Result(true, repair.message)
    }

    fun disable(context: Context): Result {
        try {
            context.stopService(Intent(context, GuardService::class.java))
        } catch (ignored: Throwable) {
        }
        SettingsGuard.setProtectionEnabled(context, false)
        return Result(true, context.getString(R.string.service_stopped))
    }

    /**
     * Makes sure GuardService is running for the currently stored configuration without
     * re-running the repair. Used by the dashboard whenever settings change while the
     * protection is already enabled.
     */
    fun ensureRunning(context: Context): Boolean = startService(context)

    /**
     * Persistent mode runs GuardService as a foreground service on Oreo+;
     * quiet mode keeps it a plain started service.
     */
    private fun startService(context: Context): Boolean = try {
        val persistent = SettingsGuard.usePersistentNotification(context)
        if (persistent) GuardService.ensureNotificationChannel(context)

        val service = Intent(context, GuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && persistent) {
            context.startForegroundService(service)
        } else {
            context.startService(service)
        }
        true
    } catch (ignored: Throwable) {
        false
    }

    class Result internal constructor(
        val success: Boolean,
        val message: String,
    )
}
