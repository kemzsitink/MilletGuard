package io.github.kemzsitink.milletguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kemzsitink.milletguard.R
import kotlinx.coroutines.launch

enum class Tab(val label: Int, val selectedIcon: ImageVector, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Rounded.Shield, Icons.Outlined.Shield),
    Apps(R.string.nav_apps, Icons.Rounded.Apps, Icons.Outlined.Apps),
    Settings(R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}

/** Lets any screen post a message without threading the host through every call. */
class Messenger(val host: SnackbarHostState, private val scope: kotlinx.coroutines.CoroutineScope) {
    fun show(text: String) {
        scope.launch { host.showSnackbar(text, duration = SnackbarDuration.Short) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppShell(vm: GuardViewModel, onLegacyLanguageChanged: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val host = remember { SnackbarHostState() }
    val messenger = remember(host, scope) { Messenger(host, scope) }

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    // Pulling down Control Center does not pause the activity, but the tile can still
    // toggle protection; re-read state whenever the window regains focus.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) vm.refresh() }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            val res = resources
            when (event) {
                UiEvent.Fine -> messenger.show(res.getString(R.string.sb_fine))
                UiEvent.Repaired -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                UiEvent.Rejected -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
                UiEvent.ReconnectSent -> messenger.show(res.getString(R.string.sb_reconnect))
                UiEvent.Saved -> messenger.show(res.getString(R.string.sb_saved))
                UiEvent.AppRefused -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    messenger.show(res.getString(R.string.sb_app_refused))
                }
                is UiEvent.AllowedAll -> launch {
                    host.currentSnackbarData?.dismiss()
                    val result = host.showSnackbar(
                        res.getQuantityString(R.plurals.sb_allowed_all, event.added.size, event.added.size),
                        actionLabel = res.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undoAllowAll(event.added)
                }
                is UiEvent.AppChanged -> launch {
                    host.currentSnackbarData?.dismiss()
                    val text = res.getString(
                        if (event.nowFree) R.string.sb_app_free else R.string.sb_app_limited,
                        event.label,
                    )
                    val result = host.showSnackbar(
                        text,
                        actionLabel = res.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undoApp(event.packageName, event.nowFree)
                }
            }
        }
    }

    BackHandler(enabled = advancedOpen) { advancedOpen = false }
    BackHandler(enabled = !advancedOpen && tab != Tab.Home) { tab = Tab.Home }

    val limitedCount = apps.rows?.count { !it.noRestrict } ?: 0

    Scaffold(
        snackbarHost = {
            // Without the bottom bar nothing else lifts snackbars above the system nav bar.
            SnackbarHost(
                host,
                Modifier.windowInsetsPadding(
                    if (advancedOpen) WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) else WindowInsets(0),
                ),
            )
        },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = !advancedOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                ShortNavigationBar {
                    Tab.entries.forEach { t ->
                        val selected = tab == t
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { tab = t },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        when {
                                            t == Tab.Home && state.health != Health.Protected -> Badge()
                                            t == Tab.Apps && limitedCount > 0 -> Badge { Text("$limitedCount") }
                                        }
                                    },
                                ) {
                                    Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = if (advancedOpen) null else tab,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 16 })
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "screen",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            Box(Modifier.fillMaxSize().padding(PaddingValues(bottom = padding.calculateBottomPadding()))) {
                when (target) {
                    null -> AdvancedScreen(vm, state, messenger, onBack = { advancedOpen = false })
                    Tab.Home -> HomeScreen(vm, state, messenger)
                    Tab.Apps -> AppsScreen(vm, state, apps, messenger)
                    Tab.Settings -> SettingsScreen(
                        vm = vm,
                        state = state,
                        messenger = messenger,
                        onOpenAdvanced = { advancedOpen = true },
                        onLegacyLanguageChanged = onLegacyLanguageChanged,
                    )
                }
            }
        }
    }
}
