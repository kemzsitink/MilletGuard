package io.github.kemzsitink.milletguard

import android.content.Context
import android.content.Intent

/**
 * Best-effort FCM/MCS reconnect kick without ADB, Shizuku or root.
 * Mirrors the heartbeat intents used by HeartbeatFixerForFCM.
 */
object FcmReconnect {
    private const val ACTION_GTALK_HEARTBEAT = "com.google.android.intent.action.GTALK_HEARTBEAT"
    private const val ACTION_MCS_HEARTBEAT = "com.google.android.intent.action.MCS_HEARTBEAT"

    private val TARGET_PACKAGES = arrayOf(Packages.GMS, Packages.GSF)

    /** Returns true if the heartbeats were sent to at least one target package. */
    fun kick(context: Context): Boolean {
        var sent = false
        for (target in TARGET_PACKAGES) {
            try {
                context.sendBroadcast(Intent(ACTION_GTALK_HEARTBEAT).setPackage(target))
                context.sendBroadcast(Intent(ACTION_MCS_HEARTBEAT).setPackage(target))
                sent = true
            } catch (ignored: Throwable) {
            }
        }
        return sent
    }
}
