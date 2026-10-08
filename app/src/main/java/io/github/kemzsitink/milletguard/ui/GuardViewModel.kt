package io.github.kemzsitink.milletguard.ui

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemzsitink.milletguard.AutostartStatusReader
import io.github.kemzsitink.milletguard.FcmAppScanner
import io.github.kemzsitink.milletguard.FcmReconnect
import io.github.kemzsitink.milletguard.GuardService
import io.github.kemzsitink.milletguard.LauncherAlias
import io.github.kemzsitink.milletguard.LocaleHelper
import io.github.kemzsitink.milletguard.Packages
import io.github.kemzsitink.milletguard.ProtectionController
import io.github.kemzsitink.milletguard.SettingsGuard
import io.github.kemzsitink.milletguard.ThemeHelper
import io.github.kemzsitink.milletguard.Whitelist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREF_AUTOSTART_CONFIRMED = "autostart_confirmed"
private const val BUSY_MIN_MS = 500L

/** Overall answer to "will my notifications arrive?". The first matching state wins. */
enum class Health { GmsMissing, NoPermission, Rejected, Blocked, Unguarded, Caveat, Protected }

enum class SetupStep { Permission, Protection, Autostart, Battery }

data class GuardState(
    val gmsInstalled: Boolean = true,
    val canWrite: Boolean = false,
    val protectionEnabled: Boolean = false,
    val gmsPresent: Boolean = false,
    val persistentNotification: Boolean = true,
    val notificationAllowed: Boolean = true,
    val whitelistRaw: String? = null,
    val whitelist: List<String> = emptyList(),
    val tileAdded: Boolean = false,
    val iconHidden: Boolean = false,
    val autoHideSettled: Boolean = false,
    val selfAutostart: AutostartStatusReader.Status = AutostartStatusReader.Status.UNKNOWN,
    val autostartConfirmed: Boolean = false,
    val selfNoRestrict: Boolean = false,
    val themeMode: String = ThemeHelper.MODE_SYSTEM,
    val language: String = "system",
    val settingsKey: String = "",
    val requiredItem: String = "",
    val rejectedMessage: String? = null,
    val permissionAttempted: Boolean = false,
    val busy: Boolean = false,
) {
    fun isDone(step: SetupStep): Boolean = when (step) {
        SetupStep.Permission -> canWrite
        SetupStep.Protection -> protectionEnabled && gmsPresent
        // "I did it" only counts while HyperOS hides the real status.
        SetupStep.Autostart -> selfAutostart == AutostartStatusReader.Status.ENABLED ||
            (selfAutostart == AutostartStatusReader.Status.UNKNOWN && autostartConfirmed)
        SetupStep.Battery -> selfNoRestrict
    }

    val doneCount: Int get() = SetupStep.entries.count { isDone(it) }
    val nextStep: SetupStep? get() = SetupStep.entries.firstOrNull { !isDone(it) }
    val notificationBlocked: Boolean get() = persistentNotification && !notificationAllowed

    val health: Health
        get() = when {
            !gmsInstalled -> Health.GmsMissing
            !canWrite -> Health.NoPermission
            rejectedMessage != null -> Health.Rejected
            !gmsPresent -> Health.Blocked
            !protectionEnabled -> Health.Unguarded
            nextStep != null || notificationBlocked -> Health.Caveat
            else -> Health.Protected
        }
}

data class AppRow(
    val packageName: String,
    val label: String,
    val stopped: Boolean,
    val autostart: AutostartStatusReader.Status,
    val noRestrict: Boolean,
)

sealed interface AppsState {
    data object NotScanned : AppsState
    data class Scanning(val previous: List<AppRow>?) : AppsState
    data class Done(val apps: List<AppRow>) : AppsState
}

val AppsState.rows: List<AppRow>?
    get() = when (this) {
        is AppsState.Done -> apps
        is AppsState.Scanning -> previous
        AppsState.NotScanned -> null
    }

private fun AppsState.mapRows(transform: (AppRow) -> AppRow): AppsState = when (this) {
    is AppsState.Done -> AppsState.Done(apps.map(transform))
    is AppsState.Scanning -> AppsState.Scanning(previous?.map(transform))
    AppsState.NotScanned -> this
}

/** One-shot UI events that the shell turns into snackbars / haptics. */
sealed interface UiEvent {
    data object Fine : UiEvent
    data object Repaired : UiEvent
    data object Rejected : UiEvent
    data object ReconnectSent : UiEvent
    data object Saved : UiEvent
    /** [added] are exactly the packages this action freed, so Undo touches nothing else. */
    data class AllowedAll(val added: Set<String>) : UiEvent
    data class AppChanged(val label: String, val packageName: String, val nowFree: Boolean) : UiEvent
    data object AppRefused : UiEvent
}

class GuardViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()

    /** Domain helpers build user-visible messages; below API 33 they need the in-app locale. */
    private val localized: Context get() = LocaleHelper.apply(ctx)
    private val prefs get() = ctx.getSharedPreferences(SettingsGuard.PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(GuardState())
    val state: StateFlow<GuardState> = _state.asStateFlow()

    private val _apps = MutableStateFlow<AppsState>(AppsState.NotScanned)
    val apps: StateFlow<AppsState> = _apps.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    private fun emit(event: UiEvent) {
        _events.trySend(event)
    }

    /** On resume: returning from HyperOS pages may have changed anything, per-app facts included. */
    fun onResume() {
        refresh()
        refreshAppFacts()
        ensureServiceRunning()
    }

    /**
     * Re-reads the global state. Synchronous and cheap, so it also runs on every window focus
     * change and after every action; the per-app queries live in [refreshAppFacts].
     */
    fun refresh() {
        val c = ctx
        val raw = SettingsGuard.read(c)
        val listed = Whitelist.parse(raw)
        val present = SettingsGuard.hasRequiredItem(c, raw)
        // Without the channel, Android reports notifications as blocked even on a fresh install.
        if (SettingsGuard.usePersistentNotification(c)) GuardService.ensureNotificationChannel(c)
        _state.update { old ->
            GuardState(
                gmsInstalled = isInstalled(c, Packages.GMS),
                canWrite = SettingsGuard.canWrite(c),
                protectionEnabled = SettingsGuard.isProtectionEnabled(c),
                gmsPresent = present,
                persistentNotification = SettingsGuard.usePersistentNotification(c),
                notificationAllowed = GuardService.canShowPersistentNotification(c),
                whitelistRaw = raw,
                whitelist = listed.toList(),
                tileAdded = SettingsGuard.isTileAdded(c),
                iconHidden = LauncherAlias.isHidden(c),
                autoHideSettled = SettingsGuard.isAutoHideSettled(c),
                selfAutostart = AutostartStatusReader.check(c, c.packageName),
                autostartConfirmed = prefs.getBoolean(PREF_AUTOSTART_CONFIRMED, false),
                selfNoRestrict = c.packageName in listed,
                themeMode = ThemeHelper.getMode(c),
                language = LocaleHelper.getLanguage(c),
                settingsKey = SettingsGuard.getConfiguredKey(c),
                requiredItem = SettingsGuard.getConfiguredRequiredItem(c),
                // A list that already contains GMS means the earlier refusal no longer matters.
                rejectedMessage = if (present) null else old.rejectedMessage,
                permissionAttempted = old.permissionAttempted,
                busy = old.busy,
            )
        }
        // The list was just read anyway, so the rows' switches stay exact after every write.
        _apps.update { apps -> apps.mapRows { it.copy(noRestrict = it.packageName in listed) } }
    }

    /**
     * Autostart and stopped state of every listed app. That is several binder calls per app,
     * so it runs off the main thread and only when HyperOS pages may have changed them.
     */
    private fun refreshAppFacts() {
        val rows = _apps.value.rows ?: return
        viewModelScope.launch {
            val facts = withContext(Dispatchers.Default) {
                rows.associate {
                    it.packageName to (AutostartStatusReader.check(ctx, it.packageName) to isStopped(ctx, it.packageName))
                }
            }
            _apps.update { apps ->
                apps.mapRows { row ->
                    facts[row.packageName]?.let { (autostart, stopped) ->
                        row.copy(autostart = autostart, stopped = stopped)
                    } ?: row
                }
            }
        }
    }

    private fun isStopped(c: Context, pkg: String): Boolean = try {
        (c.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_STOPPED) != 0
    } catch (_: Throwable) {
        false
    }

    private fun isInstalled(c: Context, pkg: String): Boolean = try {
        c.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Throwable) {
        false
    }

    /** Runs a write off the main thread with a visible busy state of at least [BUSY_MIN_MS]. */
    private fun busyAction(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            try {
                block()
            } finally {
                val left = BUSY_MIN_MS - (SystemClock.elapsedRealtime() - start)
                if (left > 0) delay(left)
                _state.update { it.copy(busy = false) }
                refresh()
            }
        }
    }

    fun markPermissionAttempted() = _state.update { it.copy(permissionAttempted = true) }

    /** Enables automatic protection (which also repairs once). */
    fun turnOnProtection() = busyAction {
        val result = withContext(Dispatchers.Default) { ProtectionController.enable(localized) }
        val present = SettingsGuard.hasRequiredItem(ctx, SettingsGuard.read(ctx))
        // enable() reports success once the service starts; the repair outcome is in message.
        if (!result.success || !present) {
            if (!result.success) SettingsGuard.setProtectionEnabled(ctx, false)
            _state.update { it.copy(rejectedMessage = result.message) }
            emit(UiEvent.Rejected)
        }
    }

    fun turnOffProtection() = busyAction {
        withContext(Dispatchers.Default) { ProtectionController.disable(localized) }
    }

    /** "Fix now" / "Try again": repair, then make sure protection stays on. */
    fun fixNow() = busyAction {
        val outcome = withContext(Dispatchers.Default) { SettingsGuard.repair(localized) }
        if (!outcome.success) {
            _state.update { it.copy(rejectedMessage = outcome.message) }
            emit(UiEvent.Rejected)
            return@busyAction
        }
        if (outcome.changed) FcmReconnect.kick(ctx)
        _state.update { it.copy(rejectedMessage = null) }
        if (!SettingsGuard.isProtectionEnabled(ctx)) {
            val enabled = withContext(Dispatchers.Default) { ProtectionController.enable(localized) }
            if (!enabled.success) {
                _state.update { it.copy(rejectedMessage = enabled.message) }
                emit(UiEvent.Rejected)
                return@busyAction
            }
        }
        emit(UiEvent.Repaired)
    }

    /** Quick action "Check now": repair only, report the outcome. */
    fun checkNow() = busyAction {
        val outcome = withContext(Dispatchers.Default) { SettingsGuard.repair(localized) }
        when {
            !outcome.success -> {
                _state.update { it.copy(rejectedMessage = outcome.message) }
                emit(UiEvent.Rejected)
            }
            outcome.changed -> {
                FcmReconnect.kick(ctx)
                _state.update { it.copy(rejectedMessage = null) }
                emit(UiEvent.Repaired)
            }
            else -> emit(UiEvent.Fine)
        }
    }

    fun reconnect() {
        FcmReconnect.kick(ctx)
        emit(UiEvent.ReconnectSent)
    }

    fun confirmAutostart() {
        prefs.edit { putBoolean(PREF_AUTOSTART_CONFIRMED, true) }
        refresh()
    }

    /** Step 4: put MilletGuard itself on the Millet no-restrict list. Returns false when refused. */
    fun allowSelfNoRestrict(): Boolean {
        val pkg = ctx.packageName
        val changed = SettingsGuard.setFcmPackagesNoRestrict(ctx, listOf(pkg), setOf(pkg))
        refresh()
        return changed >= 0
    }

    fun setPersistentNotification(enabled: Boolean) {
        SettingsGuard.setPersistentNotification(ctx, enabled)
        if (enabled) GuardService.ensureNotificationChannel(ctx)
        if (SettingsGuard.isProtectionEnabled(ctx)) ProtectionController.ensureRunning(ctx)
        refresh()
    }

    /**
     * Restarts the guard if HyperOS killed it while protection is on, in quiet mode and with
     * blocked notifications too: opening the app is the most reliable chance to revive it.
     */
    private fun ensureServiceRunning() {
        if (SettingsGuard.isProtectionEnabled(ctx)) ProtectionController.ensureRunning(ctx)
    }

    fun setThemeMode(mode: String) {
        ThemeHelper.setMode(ctx, mode)
        _state.update { it.copy(themeMode = ThemeHelper.getMode(ctx)) }
    }

    fun setLanguage(code: String) {
        LocaleHelper.setLanguage(ctx, code)
        refresh()
    }

    fun saveAdvanced(key: String, item: String) {
        SettingsGuard.saveConfig(ctx, key, item)
        if (SettingsGuard.isProtectionEnabled(ctx)) ProtectionController.ensureRunning(ctx)
        refresh()
        emit(UiEvent.Saved)
    }

    fun setIconHidden(hidden: Boolean) {
        if (hidden && !SettingsGuard.isTileAdded(ctx)) return
        if (if (hidden) LauncherAlias.hide(ctx) else LauncherAlias.show(ctx)) {
            if (hidden) SettingsGuard.setAutoHideSettled(ctx, true)
        }
        refresh()
    }

    fun markTileAdded() {
        SettingsGuard.setTileAdded(ctx, true)
        refresh()
    }

    fun settleAutoHide() {
        SettingsGuard.setAutoHideSettled(ctx, true)
        refresh()
    }

    // ---- Apps ----

    fun scanApps() {
        if (_apps.value is AppsState.Scanning) return
        _apps.value = AppsState.Scanning(_apps.value.rows)
        viewModelScope.launch {
            val rows = withContext(Dispatchers.Default) {
                val found = FcmAppScanner.scan(ctx)
                val noRestrict = SettingsGuard.readNoRestrictPackages(ctx)
                found.map {
                    AppRow(
                        packageName = it.packageName,
                        label = it.label,
                        stopped = it.stopped,
                        autostart = AutostartStatusReader.check(ctx, it.packageName),
                        noRestrict = noRestrict.contains(it.packageName),
                    )
                }
            }
            _apps.value = AppsState.Done(rows)
        }
    }

    /**
     * Writes only [packages]: those in [free] join the no-restrict list, the rest leave it.
     * Never rewrites other rows, so stale rows or overlapping Undo actions cannot clobber
     * changes made elsewhere. Returns false when HyperOS refused.
     */
    private fun writeFree(packages: Set<String>, free: Set<String>): Boolean {
        if (packages.isEmpty()) return true
        val changed = SettingsGuard.setFcmPackagesNoRestrict(ctx, packages, free)
        refresh()
        return changed >= 0
    }

    fun setAppFree(row: AppRow, free: Boolean) {
        val pkg = setOf(row.packageName)
        if (writeFree(pkg, if (free) pkg else emptySet())) {
            emit(UiEvent.AppChanged(row.label, row.packageName, free))
        } else {
            emit(UiEvent.AppRefused)
        }
    }

    fun allowAll() {
        val limited = SettingsGuard.readNoRestrictPackages(ctx).let { live ->
            _apps.value.rows.orEmpty().map { it.packageName }.filterNot { it in live }.toSet()
        }
        if (limited.isEmpty()) return
        if (writeFree(limited, limited)) emit(UiEvent.AllowedAll(limited)) else emit(UiEvent.AppRefused)
    }

    fun undoApp(packageName: String, wasFreed: Boolean) {
        val pkg = setOf(packageName)
        if (!writeFree(pkg, if (wasFreed) emptySet() else pkg)) emit(UiEvent.AppRefused)
    }

    fun undoAllowAll(added: Set<String>) {
        if (!writeFree(added, emptySet())) emit(UiEvent.AppRefused)
    }
}
