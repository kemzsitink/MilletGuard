package io.github.kemzsitink.milletguard

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

object LocaleHelper {
    private const val PREFS = "guard_state"
    private const val KEY_LANG = "ui_language"
    private const val KEY_MIGRATED = "native_locale_migrated"
    private const val SYSTEM = "system"

    /** Android 13+ uses LocaleManager; older Android keeps a small compatibility path. */
    @JvmStatic
    fun apply(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context

        val code = getLegacyLanguage(context)
        if (code == SYSTEM) return context

        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    @JvmStatic
    fun getLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager?
                ?: return SYSTEM
            val locales: LocaleList? = localeManager.applicationLocales
            if (locales == null || locales.isEmpty) return SYSTEM
            return normalize(locales[0].toLanguageTag())
        }
        return getLegacyLanguage(context)
    }

    @JvmStatic
    fun setLanguage(context: Context, code: String?) {
        val normalized = normalize(code)
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager?
                ?: return
            val locales = if (normalized == SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(normalized)
            }
            localeManager.applicationLocales = locales
            return
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LANG, normalized)
        }
    }

    /** One-time bridge from the pre-v1.4.4 custom language preference. */
    @JvmStatic
    fun migrateLegacyPreference(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val legacy = normalize(prefs.getString(KEY_LANG, SYSTEM))
        prefs.edit {
            putBoolean(KEY_MIGRATED, true)
            remove(KEY_LANG)
        }

        val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager?
        if (localeManager == null || legacy == SYSTEM) return
        val current: LocaleList? = localeManager.applicationLocales
        if (current == null || current.isEmpty) {
            localeManager.applicationLocales = LocaleList.forLanguageTags(legacy)
        }
    }

    private fun getLegacyLanguage(context: Context): String =
        normalize(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, SYSTEM)
        )

    private fun normalize(code: String?): String {
        if (code.isNullOrBlank() || SYSTEM.equals(code, ignoreCase = true)) return SYSTEM

        // Only English and Vietnamese ship translations; anything else (including a
        // stored choice from a removed language) falls back to the system locale.
        val lower = code.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("vi") -> "vi"
            lower.startsWith("en") -> "en"
            else -> SYSTEM
        }
    }
}
