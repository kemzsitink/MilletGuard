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
    fun readNoRestrictPackages(context: Context): Set<String> = parse(read(context))

    /**
     * Applies the user's checklist choice: FCM packages that are checked join the
     * Millet no-restrict whitelist, unchecked ones are removed from it, and every
     * entry that is not an FCM app (GMS, Play services, ...) is kept untouched.
     * Returns how many packages changed, or -1 when the system rejected the write.
     */
    @Synchronized
    fun setFcmPackagesNoRestrict(
        context: Context,
        fcmPackages: Collection<String>,
        selected: Set<String>,
    ): Int {
        if (!canWrite(context)) return -1
        val all = parse(read(context))
        var changed = 0
        for (pkg in fcmPackages) {
            val want = pkg in selected
            val has = pkg in all
            if (want && !has) {
                all.add(pkg)
                changed++
            } else if (!want && has) {
                all.remove(pkg)
                changed++
            }
        }
        if (changed == 0) return 0
        val value = join(all)
        return try {
            val ok = Settings.System.putString(
                context.contentResolver, getConfiguredKey(context), value,
            )
            if (!ok) return -1
            saveLastGoodIfChanged(prefs(context), getConfiguredKey(context), value)
            changed
        } catch (ignored: Throwable) {
            -1
        }
    }

    fun hasRequiredItem(context: Context, value: String?): Boolean {
        val required = getConfiguredRequiredItem(context)
        if (required.javaTrim().isEmpty() || value == null || value.javaTrim().isEmpty()) {
            return false
        }
        return value.split(',').any { required == it.javaTrim() }
    }

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
        val required = getConfiguredRequiredItem(context)
        val current = read(context)

        if (hasRequiredItem(context, current)) {
            rememberIfUseful(context, current)
            return Result(true, false, current, context.getString(R.string.already_protected))
        }

        val packages = parse(current)
        val prefs = prefs(context)
        if (packages.isEmpty()) {
            packages.addAll(parse(prefs.getString(LAST_GOOD_PREFIX + key, null)))
        }
        if (packages.isEmpty()) {
            packages.add("com.tencent.mm")
            packages.add("com.android.vending")
        }
        if (required.javaTrim().isNotEmpty()) {
            packages.add(required.javaTrim())
        }

        val repaired = join(packages)
        return try {
            val ok = Settings.System.putString(context.contentResolver, key, repaired)
            if (ok) {
                saveLastGoodIfChanged(prefs, key, repaired)
                Result(true, true, repaired, context.getString(R.string.repaired))
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
        if (current == null || current.javaTrim().isEmpty()) return
        val key = getConfiguredKey(context)
        val packages = parse(current)
        if (packages.isEmpty()) return
        saveLastGoodIfChanged(prefs(context), key, join(packages))
    }

    private fun saveLastGoodIfChanged(prefs: SharedPreferences, key: String, value: String) {
        val prefKey = LAST_GOOD_PREFIX + key
        if (value != prefs.getString(prefKey, null)) {
            prefs.edit { putString(prefKey, value) }
        }
    }

    private fun parse(value: String?): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        value?.split(',')?.forEach { part ->
            val p = part.javaTrim()
            if (p.isNotEmpty()) out.add(p)
        }
        return out
    }

    private fun join(items: Set<String>): String = items.joinToString(",")

    /** java.lang.String.trim() semantics (strips chars <= U+0020), kept for exact parity. */
    private fun String.javaTrim(): String = trim { it <= ' ' }

    class Result internal constructor(
        val success: Boolean,
        val changed: Boolean,
        val value: String?,
        val message: String,
    )
}
