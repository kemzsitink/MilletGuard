package io.github.kemzsitink.milletguard

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Runtime control of the launcher activity-alias.
 *
 * MainActivity itself stays enabled so the Quick Settings tile long-press
 * (ACTION_QS_TILE_PREFERENCES), explicit intents, and BootReceiver keep working while
 * the home-screen entry is hidden.
 */
object LauncherAlias {

    const val COMPONENT = "io.github.kemzsitink.milletguard.LauncherAlias"

    @JvmStatic
    fun isHidden(context: Context): Boolean = try {
        val state = context.packageManager.getComponentEnabledSetting(
            ComponentName(context, COMPONENT)
        )
        state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    fun hide(context: Context): Boolean = set(context, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)

    @JvmStatic
    fun show(context: Context): Boolean = set(context, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

    private fun set(context: Context, state: Int): Boolean = try {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, COMPONENT),
            state,
            PackageManager.DONT_KILL_APP
        )
        true
    } catch (_: Throwable) {
        false
    }
}
