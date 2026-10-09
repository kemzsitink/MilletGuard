package io.github.kemzsitink.milletguard

import android.content.Context
import androidx.core.content.edit

object ThemeHelper {
    private const val PREFS = SettingsGuard.PREFS
    private const val KEY_MODE = "appearance_mode"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    @JvmStatic
    fun getMode(context: Context): String {
        val mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_SYSTEM)
        return normalize(mode)
    }

    @JvmStatic
    fun setMode(context: Context, mode: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_MODE, normalize(mode))
        }
    }

    private fun normalize(mode: String?): String = when (mode) {
        MODE_LIGHT -> MODE_LIGHT
        MODE_DARK -> MODE_DARK
        else -> MODE_SYSTEM
    }
}
