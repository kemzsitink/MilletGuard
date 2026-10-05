package io.github.kemzsitink.milletguard.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.kemzsitink.milletguard.GuardTileService
import io.github.kemzsitink.milletguard.HyperOsSettings
import io.github.kemzsitink.milletguard.R
import java.util.Locale

/** Deep links into system / HyperOS / Google Play services screens. All are best effort. */
object SystemIntents {
    private const val GMS = "com.google.android.gms"
    private val DIAGNOSTICS = listOf(
        "com.google.android.gms.gcm.GcmDiagnostics",
        "com.google.android.gms.gtalkservice.diagnostics.GTalkServiceDiagnostics",
    )

    private fun Context.tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    private fun Context.resolves(intent: Intent): Boolean =
        intent.resolveActivity(packageManager) != null

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()

    fun openWriteSettings(context: Context): Boolean =
        context.tryStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(context)))

    fun openNotificationSettings(context: Context): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
        }
        return context.tryStart(intent)
    }

    fun openAutostart(context: Context): Boolean = HyperOsSettings.openAutoStartManager(context)

    /** HyperOS per-app permission page (battery saver lives there), with a generic fallback. */
    fun openAppPermissions(context: Context, pkg: String): Boolean =
        HyperOsSettings.openAppPermissionEditor(context, pkg)

    fun openApp(context: Context, pkg: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return context.tryStart(launch)
    }

    private fun diagnosticsIntent(context: Context): Intent? {
        for (name in DIAGNOSTICS) {
            val intent = Intent().setClassName(GMS, name)
            if (context.resolves(intent)) return intent
        }
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(GMS, PackageManager.GET_ACTIVITIES)
                .activities.orEmpty()
                .map { it.name.orEmpty() }
                .firstOrNull {
                    val lower = it.lowercase(Locale.ROOT)
                    (lower.contains("gcm") || lower.contains("fcm") || lower.contains("gtalk")) &&
                        lower.contains("diagnostic")
                }
                ?.let { Intent().setClassName(GMS, it) }
        } catch (_: Throwable) {
            null
        }
    }

    fun hasDiagnostics(context: Context): Boolean = diagnosticsIntent(context) != null

    fun openDiagnostics(context: Context): Boolean =
        diagnosticsIntent(context)?.let { context.tryStart(it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)) } ?: false

    fun hasAutostart(context: Context): Boolean =
        context.resolves(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)) ||
            context.resolves(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    ),
                ),
            )

    /** True when the system add-tile dialog exists (Android 13+). */
    val canRequestTile: Boolean get() = Build.VERSION.SDK_INT >= 33

    /** Shows the system "Add tile" dialog; [onResult] receives true when the tile is in the panel. */
    fun requestAddTile(context: Context, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onResult(false)
            return
        }
        try {
            val statusBar = context.getSystemService(StatusBarManager::class.java)
                ?: return onResult(false)
            statusBar.requestAddTileService(
                ComponentName(context, GuardTileService::class.java),
                context.getString(R.string.tile_label),
                Icon.createWithResource(context, R.drawable.app_icon),
                ContextCompat.getMainExecutor(context),
            ) { result ->
                onResult(
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                )
            }
        } catch (_: Throwable) {
            onResult(false)
        }
    }
}
