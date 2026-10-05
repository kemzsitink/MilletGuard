package io.github.kemzsitink.milletguard.ui

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kemzsitink.milletguard.R
import io.github.kemzsitink.milletguard.ThemeHelper

/** A segmented group: section header plus rows that share one rounded container. */
private fun LazyListScope.group(title: Int, rows: List<@Composable (shapesIndex: Int, count: Int) -> Unit>) {
    item(key = "h$title") { SectionHeader(stringResource(title), Modifier.widthIn(max = 600.dp)) }
    rows.forEachIndexed { i, row ->
        item(key = "r$title-$i") {
            Column(Modifier.widthIn(max = 600.dp).padding(bottom = ListItemDefaults.SegmentedGap)) {
                row(i, rows.size)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    vm: GuardViewModel,
    state: GuardState,
    messenger: Messenger,
    onOpenAdvanced: () -> Unit,
    onLegacyLanguageChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var languageSheet by rememberSaveable { mutableStateOf(false) }
    var tileHelp by rememberSaveable { mutableStateOf(false) }
    var howOpen by rememberSaveable { mutableStateOf(false) }
    val hasDiagnostics = remember { SystemIntents.hasDiagnostics(context) }
    val hasAutostart = remember { SystemIntents.hasAutostart(context) }
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scroll)
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = pagePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            group(
                R.string.sec_protection,
                listOf(
                    { i, n ->
                        SegmentedListItem(
                            checked = state.protectionEnabled,
                            onCheckedChange = { if (it) vm.turnOnProtection() else vm.turnOffProtection() },
                            enabled = state.canWrite && !state.busy,
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = { Text(stringResource(R.string.auto_protect_sub)) },
                            trailingContent = { Switch(checked = state.protectionEnabled, onCheckedChange = null) },
                        ) { Text(stringResource(R.string.auto_protect)) }
                    },
                    { i, n ->
                        val notifLabel = stringResource(R.string.notif)
                        if (state.notificationBlocked) {
                            SegmentedListItem(
                                onClick = { SystemIntents.openNotificationSettings(context) },
                                shapes = ListItemDefaults.segmentedShapes(i, n),
                                colors = groupColors(),
                                supportingContent = {
                                    Text(stringResource(R.string.notif_blocked), color = MaterialTheme.colorScheme.error)
                                },
                                // Shows the real preference; turning it off is how to choose quiet mode.
                                trailingContent = {
                                    Switch(
                                        checked = state.persistentNotification,
                                        onCheckedChange = { vm.setPersistentNotification(it) },
                                        modifier = Modifier.semantics { contentDescription = notifLabel },
                                    )
                                },
                            ) { Text(stringResource(R.string.notif)) }
                        } else {
                            SegmentedListItem(
                                checked = state.persistentNotification,
                                onCheckedChange = { on ->
                                    vm.setPersistentNotification(on)
                                    if (on && !vm.state.value.notificationAllowed) {
                                        SystemIntents.openNotificationSettings(context)
                                    }
                                },
                                shapes = ListItemDefaults.segmentedShapes(i, n),
                                colors = groupColors(),
                                supportingContent = { Text(stringResource(R.string.notif_sub)) },
                                trailingContent = { Switch(checked = state.persistentNotification, onCheckedChange = null) },
                            ) { Text(stringResource(R.string.notif)) }
                        }
                    },
                ),
            )

            group(
                R.string.sec_shortcut,
                listOf(
                    { i, n ->
                        SegmentedListItem(
                            onClick = {
                                if (SystemIntents.canRequestTile) {
                                    SystemIntents.requestAddTile(context) { added -> if (added) vm.markTileAdded() }
                                } else {
                                    tileHelp = true
                                }
                            },
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = {
                                Text(stringResource(if (state.tileAdded) R.string.tile_added else R.string.tile_absent))
                            },
                        ) { Text(stringResource(R.string.tile)) }
                    },
                    { i, n ->
                        val supporting = when {
                            state.iconHidden -> R.string.icon_hidden_note
                            !state.tileAdded -> R.string.hide_needs_tile
                            else -> R.string.hide_icon_warn
                        }
                        SegmentedListItem(
                            checked = state.iconHidden,
                            onCheckedChange = vm::setIconHidden,
                            enabled = state.tileAdded || state.iconHidden,
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = { Text(stringResource(supporting)) },
                            trailingContent = { Switch(checked = state.iconHidden, onCheckedChange = null) },
                        ) { Text(stringResource(R.string.hide_icon)) }
                    },
                ),
            )

            group(
                R.string.sec_appearance,
                listOf(
                    { i, n ->
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = { ThemeToggles(state.themeMode, vm::setThemeMode) },
                        ) { Text(stringResource(R.string.theme)) }
                    },
                    { i, n ->
                        SegmentedListItem(
                            // The system per-app language page closes itself immediately for this
                            // targetSdk 22 app on HyperOS, so the in-app picker is the only path.
                            onClick = { languageSheet = true },
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = { Text(languageLabel(state.language)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                        ) { Text(stringResource(R.string.language)) }
                    },
                ),
            )

            group(
                R.string.sec_trouble,
                listOf(
                    { i, n ->
                        SegmentedListItem(
                            onClick = { SystemIntents.openDiagnostics(context) },
                            enabled = hasDiagnostics,
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = if (hasDiagnostics) null else ({ Text(stringResource(R.string.not_available)) }),
                        ) { Text(stringResource(R.string.diag)) }
                    },
                    { i, n ->
                        SegmentedListItem(
                            onClick = { SystemIntents.openAutostart(context) },
                            enabled = hasAutostart,
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = if (hasAutostart) null else ({ Text(stringResource(R.string.not_available)) }),
                        ) { Text(stringResource(R.string.hyperos_autostart)) }
                    },
                    { i, n ->
                        SegmentedListItem(
                            onClick = onOpenAdvanced,
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = { Text(stringResource(R.string.advanced_sub)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                        ) { Text(stringResource(R.string.advanced)) }
                    },
                ),
            )

            group(
                R.string.sec_about,
                listOf(
                    { i, n ->
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            supportingContent = { Text(stringResource(R.string.version, version)) },
                        ) { Text(stringResource(R.string.app_name)) }
                    },
                    { i, n ->
                        SegmentedListItem(
                            onClick = { howOpen = !howOpen },
                            shapes = ListItemDefaults.segmentedShapes(i, n),
                            colors = groupColors(),
                            modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                            supportingContent = if (howOpen) ({ Text(stringResource(R.string.how_body)) }) else null,
                            trailingContent = {
                                Icon(if (howOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                            },
                        ) { Text(stringResource(R.string.how_it_works)) }
                    },
                ),
            )
        }
    }

    if (languageSheet) {
        LanguageSheet(
            current = state.language,
            onPick = { code ->
                languageSheet = false
                if (code != state.language) {
                    vm.setLanguage(code)
                    if (Build.VERSION.SDK_INT < 33) onLegacyLanguageChanged()
                }
            },
            onDismiss = { languageSheet = false },
        )
    }
    if (tileHelp) TileHelpSheet(onDismiss = { tileHelp = false })
}

@Composable
private fun languageLabel(code: String): String = when (code) {
    "en" -> "English"
    "vi" -> "Tiếng Việt"
    else -> stringResource(R.string.lang_system)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeToggles(mode: String, onPick: (String) -> Unit) {
    val modes = listOf(
        ThemeHelper.MODE_SYSTEM to R.string.theme_system,
        ThemeHelper.MODE_LIGHT to R.string.theme_light,
        ThemeHelper.MODE_DARK to R.string.theme_dark,
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = mode == value,
                onCheckedChange = { onPick(value) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                // The row sits on surfaceContainer, so unchecked buttons need their own fill.
                colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(stringResource(label), textAlign = TextAlign.Center) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            listOf("system", "en", "vi").forEach { code ->
                ListItem(
                    selected = current == code,
                    onClick = { onPick(code) },
                    leadingContent = { RadioButton(selected = current == code, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                ) { Text(languageLabel(code)) }
            }
        }
    }
}
