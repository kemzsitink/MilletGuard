package io.github.kemzsitink.milletguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/** HyperOS/MIUI settings deep links with safe Android fallbacks. */
object HyperOsSettings {
    private const val SECURITY_CENTER = Packages.SECURITY_CENTER

    fun openAutoStartManager(context: Context): Boolean {
        val intent = Intent("miui.intent.action.OP_AUTO_START")
            .addCategory(Intent.CATEGORY_DEFAULT)
        if (launch(context, intent)) return true

        val explicit = Intent().setComponent(
            ComponentName(SECURITY_CENTER, "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        if (launch(context, explicit)) return true

        return launch(context, Intent(Settings.ACTION_APPLICATION_SETTINGS))
    }

    fun openAppPermissionEditor(context: Context, packageName: String): Boolean {
        val activityNames = listOf(
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
        )

        for (activityName in activityNames) {
            val intent = Intent()
                .setComponent(ComponentName(SECURITY_CENTER, activityName))
                .putExtra("extra_pkgname", packageName)
                .putExtra("package_name", packageName)
            if (launch(context, intent)) return true
        }

        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            .addCategory(Intent.CATEGORY_DEFAULT)
        return launch(context, details)
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        if (intent.resolveActivity(context.packageManager) == null) {
            false
        } else {
            context.startActivity(intent)
            true
        }
    } catch (ignored: Throwable) {
        false
    }
}
