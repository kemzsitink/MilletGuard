package io.github.kemzsitink.milletguard

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.net.toUri

/**
 * Quick Settings (Control Center) tile that mirrors the dashboard protection switch.
 *
 * Uses the standard listening mode: onStartListening fires every time the panel is
 * opened, which keeps the tile truthful without any background work of its own. A tap
 * runs the exact same enable/disable sequence as the dashboard via ProtectionController.
 */
class GuardTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        SettingsGuard.setTileAdded(this, true)
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        SettingsGuard.setTileAdded(this, false)
        // The tile was the only entry point left while the icon is hidden.
        if (LauncherAlias.isHidden(this)) LauncherAlias.show(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (SettingsGuard.isProtectionEnabled(this)) {
            ProtectionController.disable(this)
        } else if (!SettingsGuard.canWrite(this)) {
            // A tile tap is a user gesture, so collapsing the panel into the system
            // WRITE_SETTINGS page is allowed here without any background-start concern.
            openWriteSettings()
        } else {
            val result = ProtectionController.enable(this)
            if (!result.success && getString(R.string.permission_missing) == result.message) {
                openWriteSettings()
            }
        }
        refreshTile()
    }

    // The Intent overload is only used below API 34, where the PendingIntent one is missing.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openWriteSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            "package:$packageName".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
                )
            } else {
                startActivityAndCollapse(intent)
            }
        } catch (_: Throwable) {
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return

        val enabled = SettingsGuard.isProtectionEnabled(this)
        // Same "healthy" rule as the dashboard headline.
        val healthy = enabled && SettingsGuard.canWrite(this) &&
            SettingsGuard.hasRequiredItem(this, SettingsGuard.read(this))
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Tile.setStateDescription only exists from API 30.
            tile.stateDescription = getString(
                when {
                    !enabled -> R.string.status_disabled
                    healthy -> R.string.status_protected
                    else -> R.string.status_attention
                },
            )
        }
        tile.updateTile()
    }
}
