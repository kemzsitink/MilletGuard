package io.github.kemzsitink.milletguard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kemzsitink.milletguard.ui.AppShell
import io.github.kemzsitink.milletguard.ui.GuardViewModel
import io.github.kemzsitink.milletguard.ui.theme.MilletGuardTheme
import io.github.kemzsitink.milletguard.ui.theme.isAppInDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: GuardViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        // Android 13+ uses the system per-app language; older builds need the legacy override.
        super.attachBaseContext(LocaleHelper.apply(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleHelper.migrateLegacyPreference(this)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = isAppInDarkTheme(state.themeMode)
            LaunchedEffect(dark) {
                // System bar icons follow the in-app theme choice, not only the system one.
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    // API 24-25 cannot draw dark nav icons; that fallback needs a dark scrim.
                    SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x80, 0x1B, 0x1B, 0x1B))
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MilletGuardTheme(dark = dark) {
                AppShell(vm = vm, onLegacyLanguageChanged = ::recreate)
            }
        }
    }
}
