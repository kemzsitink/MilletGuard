package io.github.kemzsitink.milletguard

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit

object SettingsGuard {
    const val PREFS = "guard_state"
    const val PREF_ENABLED = "enabled"
    const val PREF_PERSISTENT_NOTIFICATION = "persistent_notification"
    private const val PREF_SETTINGS_KEY = "settings_key"
    private const val PREF_REQUIRED_ITEM = "required_item"
    private const val PREF_TILE_ADDED = "tile_added"
    private const val PREF_AUTO_HIDE_SETTLED = "auto_hide_settled"
    private const val PREF_PINNED = "pinned"
    private const val LAST_GOOD_PREFIX = "last_good_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun getConfiguredKey(context: Context): String {
        val fallback = context.getString(R.string.default_key)
        return prefs(context).getString(PREF_SETTINGS_KEY, fallback) ?: fallback
    }

    fun getConfiguredRequiredItem(context: Context): String {
        val fallback = context.getString(R.string.default_required_item)
        return prefs(context).getString(PREF_REQUIRED_ITEM, fallback) ?: fallback
    }

    fun isProtectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_ENABLED, false)

    fun usePersistentNotification(context: Context): Boolean =
        prefs(context).getBoolean(PREF_PERSISTENT_NOTIFICATION, true)

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(PREF_ENABLED, enabled) }
    }

    fun setPersistentNotification(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(PREF_PERSISTENT_NOTIFICATION, enabled) }
    }

    fun isTileAdded(context: Context): Boolean =
        prefs(context).getBoolean(PREF_TILE_ADDED, false)

    fun setTileAdded(context: Context, added: Boolean) {
        prefs(context).edit { putBoolean(PREF_TILE_ADDED, added) }
    }

    /**
     * True once the auto-hide flow has been settled (hidden, declined, or dismissed).
     * The onboarding prompt must never nag the user again after that point.
     */
    fun isAutoHideSettled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_AUTO_HIDE_SETTLED, false)

    fun setAutoHideSettled(context: Context, settled: Boolean) {
        prefs(context).edit { putBoolean(PREF_AUTO_HIDE_SETTLED, settled) }
    }

    fun saveConfig(context: Context, key: String?, requiredItem: String?) {
        prefs(context).edit {
            putString(PREF_SETTINGS_KEY, safe(key, context.getString(R.string.default_key)))
            putString(
                PREF_REQUIRED_ITEM,
                safe(requiredItem, context.getString(R.string.default_required_item)),
            )
        }
    }

    private fun safe(s: String?, fallback: String): String =
        s?.javaTrim()?.takeIf { it.isNotEmpty() } ?: fallback

    fun read(context: Context): String? =
        Settings.System.getString(context.contentResolver, getConfiguredKey(context))

    /** Package names currently exempt from HyperOS Millet background limits. */
    fun readNoRestrictPackages(context: Context): Set<String> = Whitelist.parse(read(context))

    /**
     * Packages MilletGuard keeps on the list next to the required item: MilletGuard itself
     * after setup step 4 and the apps freed from the Apps tab. HyperOS rebuilds the list from
     * its own per-app battery settings and drops them; [repair] puts them back.
     */
    @Synchronized
    fun pinnedPackages(context: Context): Set<String> {
        val prefs = prefs(context)
        prefs.getStringSet(PREF_PINNED, null)?.let { return it.toSet() }
        // Before pins existed, step 4 lived only in the list itself: carry it over once.
        val self = context.packageName
        val initial = if (self in readNoRestrictPackages(context)) setOf(self) else emptySet()
        prefs.edit { putStringSet(PREF_PINNED, initial) }
        return initial
    }

    /**
     * Applies the user's checklist choice: FCM packages that are checked join the
     * Millet no-restrict whitelist, unchecked ones are removed from it, and every
     * entry that is not an FCM app (GMS, Play services, ...) is kept untouched.
     * The choice is also pinned, so it survives HyperOS rebuilding the list.
     * Returns how many packages changed, or -1 when the system rejected the write.
     */
    @Synchronized
    fun setFcmPackagesNoRestrict(
        context: Context,
        fcmPackages: Collection<String>,
        selected: Set<String>,
    ): Int {
        if (!canWrite(context)) return -1
        val selection = Whitelist.select(read(context), fcmPackages, selected)
        if (selection.changed > 0) {
            val key = getConfiguredKey(context)
            try {
                if (!Settings.System.putString(context.contentResolver, key, selection.value)) return -1
            } catch (ignored: Throwable) {
                return -1
            }
            saveLastGoodIfChanged(prefs(context), key, selection.value)
        }
        val pinned = pinnedPackages(context).toMutableSet()
        for (pkg in fcmPackages) if (pkg in selected) pinned.add(pkg) else pinned.remove(pkg)
        prefs(context).edit { putStringSet(PREF_PINNED, pinned) }
        return selection.changed
    }

    fun hasRequiredItem(context: Context, value: String?): Boolean =
        Whitelist.contains(value, getConfiguredRequiredItem(context))

    /**
     * Repairs only when necessary. A no-op repair performs no Settings write and, unless
     * the actual package list changed, no SharedPreferences write either. This is
     * important because the common background path should be practically idle.
     */
    @Synchronized
    fun repair(context: Context): Result {
        if (!canWrite(context)) {
            return Result(false, false, read(context), context.getString(R.string.permission_missing))
        }

        val key = getConfiguredKey(context)
        val current = read(context)
        val prefs = prefs(context)
        val requiredItem = getConfiguredRequiredItem(context)
        // An uninstalled pin is skipped, not dropped, so reinstalling the app keeps the choice.
        val required = listOf(requiredItem) + pinnedPackages(context).filter { isInstalled(context, it) }
        val repaired = Whitelist.repaired(current, prefs.getString(LAST_GOOD_PREFIX + key, null), required)
        if (repaired == null) {
            rememberIfUseful(context, current)
            return Result(true, false, current, context.getString(R.string.already_protected))
        }

        return try {
            val ok = Settings.System.putString(context.contentResolver, key, repaired)
            if (ok) {
                saveLastGoodIfChanged(prefs, key, repaired)
                Result(
                    true, true, repaired, context.getString(R.string.repaired),
                    requiredRestored = !Whitelist.contains(current, requiredItem),
                )
            } else {
                Result(false, false, current, context.getString(R.string.write_rejected))
            }
        } catch (t: Throwable) {
            Result(
                false, false, current,
                context.getString(R.string.write_failed, t.javaClass.simpleName),
            )
        }
    }

    @Synchronized
    fun rememberIfUseful(context: Context) {
        rememberIfUseful(context, read(context))
    }

    private fun rememberIfUseful(context: Context, current: String?) {
        val packages = Whitelist.parse(current)
        if (packages.isEmpty()) return
        saveLastGoodIfChanged(prefs(context), getConfiguredKey(context), Whitelist.join(packages))
    }

    private fun saveLastGoodIfChanged(prefs: SharedPreferences, key: String, value: String) {
        val prefKey = LAST_GOOD_PREFIX + key
        if (value != prefs.getString(prefKey, null)) {
            prefs.edit { putString(prefKey, value) }
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (ignored: Throwable) {
        false
    }

    class Result internal constructor(
        val success: Boolean,
        val changed: Boolean,
        val value: String?,
        val message: String,
        /** The required item (GMS) was missing and is back: time to wake it up. */
        val requiredRestored: Boolean = false,
    )
}
