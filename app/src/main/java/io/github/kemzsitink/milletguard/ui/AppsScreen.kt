package io.github.kemzsitink.milletguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kemzsitink.milletguard.AutostartStatusReader.Status
import io.github.kemzsitink.milletguard.R

private const val WHATSAPP = "com.whatsapp"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppsScreen(vm: GuardViewModel, state: GuardState, apps: AppsState, messenger: Messenger) {
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (apps is AppsState.NotScanned) vm.scanApps() }

    val rows = apps.rows
    val limited = rows.orEmpty().filter { !it.noRestrict }
    val ok = rows.orEmpty().filter { it.noRestrict }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.apps_title)) },
                subtitle = rows?.let { { Text(stringResource(R.string.apps_subtitle, it.size, limited.size)) } },
                actions = {
                    if (apps is AppsState.Done) {
                        IconButton(onClick = vm::scanApps) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.rescan))
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (apps is AppsState.Scanning && apps.previous != null) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PageGutter))
            }
            when {
                rows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        ContainedLoadingIndicator()
                        Spacer(Modifier.size(16.dp))
                        Text(stringResource(R.string.scanning), textAlign = TextAlign.Center)
                    }
                }
                rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.apps_empty), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = vm::scanApps) { Text(stringResource(R.string.rescan)) }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = pagePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!state.canWrite) {
                        item(key = "perm") {
                            NeedPermissionCard(
                                onAllow = { performStep(SetupStep.Permission, vm, context, messenger) },
                                modifier = Modifier.widthIn(max = 600.dp).padding(bottom = 8.dp),
                            )
                        }
                    }
                    if (apps is AppsState.Done && rows.none { it.autostart != Status.UNKNOWN }) {
                        item(key = "as") {
                            val canOpen = remember { SystemIntents.hasAutostart(context) }
                            ListItem(
                                leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                supportingContent = {
                                    TextButton(onClick = { SystemIntents.openAutostart(context) }, enabled = canOpen) {
                                        Text(stringResource(R.string.open_autostart))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.widthIn(max = 600.dp),
                            ) { Text(stringResource(R.string.as_hidden)) }
                        }
                    }
                    if (limited.isNotEmpty()) {
                        item(key = "h_limited") {
                            // FlowRow lets the button drop below the title when space is tight.
                            FlowRow(
                                Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        stringResource(R.string.grp_limited),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.semantics { heading() },
                                    )
                                }
                                if (state.canWrite) {
                                    FilledTonalButton(onClick = vm::allowAll, shapes = ButtonDefaults.shapes()) {
                                        Text(stringResource(R.string.allow_all, limited.size))
                                    }
                                }
                            }
                        }
                        appGroup(limited, state.canWrite, vm, onOpen = { sheetFor = it.packageName })
                    }
                    if (ok.isNotEmpty()) {
                        item(key = "h_ok") {
                            SectionHeader(stringResource(R.string.grp_ok), Modifier.widthIn(max = 600.dp))
                        }
                        appGroup(ok, state.canWrite, vm, onOpen = { sheetFor = it.packageName })
                    }
                }
            }
        }
    }

    val sheetRow = rows?.firstOrNull { it.packageName == sheetFor }
    if (sheetRow != null) {
        AppSheet(sheetRow, state.canWrite, vm, onDismiss = { sheetFor = null })
    }
}

@Composable
private fun appFacts(row: AppRow): String {
    val facts = buildList {
        add(stringResource(if (row.noRestrict) R.string.st_free else R.string.st_limited))
        when (row.autostart) {
            Status.DISABLED -> add(stringResource(R.string.st_as_off))
            Status.PARTIAL -> add(stringResource(R.string.st_as_partial))
            else -> Unit
        }
        if (row.stopped) add(stringResource(R.string.st_stopped))
    }
    return facts.take(2).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.appGroup(
    group: List<AppRow>,
    canWrite: Boolean,
    vm: GuardViewModel,
    onOpen: (AppRow) -> Unit,
) {
    itemsIndexed(group, key = { _, r -> r.packageName }) { index, row ->
        val switchCd = stringResource(R.string.switch_cd, row.label)
        Column(Modifier.widthIn(max = 600.dp).padding(bottom = ListItemDefaults.SegmentedGap).animateItem()) {
            SegmentedListItem(
                onClick = { onOpen(row) },
                shapes = ListItemDefaults.segmentedShapes(index, group.size),
                colors = groupColors(),
                leadingContent = { AppIcon(row.packageName) },
                supportingContent = { Text(appFacts(row)) },
                trailingContent = {
                    Switch(
                        checked = row.noRestrict,
                        onCheckedChange = { vm.setAppFree(row, it) },
                        enabled = canWrite,
                        modifier = Modifier.semantics { contentDescription = switchCd },
                    )
                },
            ) { Text(row.label) }
            if (row.packageName == WHATSAPP && !row.noRestrict) {
                Text(
                    stringResource(R.string.whatsapp_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun NeedPermissionCard(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.need_perm), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(12.dp))
            Button(onClick = onAllow, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.btn_allow)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppSheet(row: AppRow, canWrite: Boolean, vm: GuardViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                leadingContent = { AppIcon(row.packageName, 48.dp) },
                supportingContent = {
                    Text(row.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { Text(row.label, style = MaterialTheme.typography.titleMedium) }
            ListItem(
                checked = row.noRestrict,
                onCheckedChange = { vm.setAppFree(row, it) },
                enabled = canWrite,
                trailingContent = { Switch(checked = row.noRestrict, onCheckedChange = null, enabled = canWrite) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { Text(stringResource(R.string.det_norestrict)) }
            val autostart = when (row.autostart) {
                Status.ENABLED -> stringResource(R.string.det_autostart, stringResource(R.string.as_on))
                Status.PARTIAL -> stringResource(R.string.det_autostart, stringResource(R.string.as_partial_v))
                Status.DISABLED -> stringResource(R.string.det_autostart, stringResource(R.string.as_off_v))
                Status.UNKNOWN -> stringResource(R.string.det_check_hyperos)
            }
            ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent)) { Text(autostart) }
            if (row.stopped) {
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent)) { Text(stringResource(R.string.st_stopped)) }
            }
            if (row.packageName == WHATSAPP) {
                Text(
                    stringResource(R.string.whatsapp_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { SystemIntents.openAppPermissions(context, row.packageName) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.open_hyperos)) }
                TextButton(onClick = { SystemIntents.openApp(context, row.packageName) }) {
                    Text(stringResource(R.string.open_app))
                }
            }
        }
    }
}
