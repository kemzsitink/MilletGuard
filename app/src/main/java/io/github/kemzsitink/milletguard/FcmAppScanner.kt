package io.github.kemzsitink.milletguard

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

/**
 * Best-effort detector for installed apps that declare standard FCM/GCM receive
 * components. It does not inspect traffic or accounts and runs only when requested.
 */
object FcmAppScanner {
    private const val ACTION_FCM = "com.google.firebase.MESSAGING_EVENT"
    private const val ACTION_GCM_RECEIVE = "com.google.android.c2dm.intent.RECEIVE"

    class AppEntry internal constructor(
        val packageName: String,
        val label: String,
        val stopped: Boolean,
    )

    fun scan(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_ALL

        // Insertion-ordered set of candidate packages.
        val packages = LinkedHashSet<String>()

        try {
            pm.queryIntentServices(Intent(ACTION_FCM), flags).forEach { resolveInfo ->
                resolveInfo.serviceInfo?.packageName?.let(packages::add)
            }
        } catch (ignored: Throwable) {
        }

        // Legacy GCM / compatibility path still used by some apps and libraries.
        try {
            pm.queryBroadcastReceivers(Intent(ACTION_GCM_RECEIVE), flags).forEach { resolveInfo ->
                resolveInfo.activityInfo?.packageName?.let(packages::add)
            }
        } catch (ignored: Throwable) {
        }

        val result = ArrayList<AppEntry>()
        for (packageName in packages) {
            if (packageName == context.packageName ||
                packageName == "com.google.android.gms" ||
                packageName == "com.android.vending"
            ) {
                continue
            }

            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                if (!appInfo.enabled) continue
                // Keep the list focused on user-facing apps that can actually be opened.
                if (pm.getLaunchIntentForPackage(packageName) == null) continue
                val label = pm.getApplicationLabel(appInfo).toString().trim()
                    .ifEmpty { packageName }
                val stopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
                result.add(AppEntry(packageName, label, stopped))
            } catch (ignored: Throwable) {
            }
        }

        result.sortWith(
            compareBy<AppEntry> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName },
        )
        return result
    }
}
