package io.github.kemzsitink.milletguard

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.net.toUri
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort FCM reconnect without ADB, Shizuku or root.
 *
 * A frozen GMS cannot act on a broadcast, and with the screen off HyperOS's Greezer does not
 * even deliver one to it. A content provider call is different: Greezer thaws the callee for
 * it whoever the caller is. So [recover] first touches an exported GMS provider, gives the
 * asynchronous thaw a moment, and only then asks GMS to reconnect.
 */
object FcmReconnect {
    /** Exported by GMS for every Play services client (DynamiteModule queries it). */
    private const val THAW_AUTHORITY = "com.google.android.gms.chimera"
    private const val THAW_SETTLE_MS = 2_000L

    private val ACTIONS = arrayOf(
        // What Firebase's own test helper sends to kick a hung or disconnected GCM.
        "com.google.android.intent.action.GCM_RECONNECT",
        "com.google.android.gms.gcm.ACTION_HEARTBEAT_NOW",
        // Older Play services builds (HeartbeatFixerForGCM).
        "com.google.android.intent.action.GTALK_HEARTBEAT",
        "com.google.android.intent.action.MCS_HEARTBEAT",
    )

    private val TARGET_PACKAGES = arrayOf(Packages.GMS, Packages.GSF)

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "fcm-recover") }
    private val pending = AtomicBoolean(false)

    /**
     * Thaws GMS, then asks it to reconnect. Runs off the calling thread (the provider call is
     * a binder call into GMS); requests made before the reconnect goes out share it.
     */
    fun recover(context: Context) {
        if (!pending.compareAndSet(false, true)) return
        val app = context.applicationContext
        worker.execute {
            try {
                thaw(app)
                SystemClock.sleep(THAW_SETTLE_MS)
            } finally {
                pending.set(false)
            }
            kick(app)
        }
    }

    /** Any call into the provider makes Greezer thaw GMS, even one that fails. */
    private fun thaw(context: Context) {
        try {
            context.contentResolver.acquireUnstableContentProviderClient(THAW_AUTHORITY)?.use { client ->
                client.query("content://$THAW_AUTHORITY".toUri(), null, null, null, null)?.close()
            }
        } catch (ignored: Throwable) {
        }
    }

    private fun kick(context: Context) {
        for (target in TARGET_PACKAGES) {
            for (action in ACTIONS) {
                try {
                    context.sendBroadcast(Intent(action).setPackage(target))
                } catch (ignored: Throwable) {
                }
            }
        }
    }
}
