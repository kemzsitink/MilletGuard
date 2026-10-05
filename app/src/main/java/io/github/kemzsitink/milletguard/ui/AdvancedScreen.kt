package io.github.kemzsitink.milletguard.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.kemzsitink.milletguard.LauncherAlias
import io.github.kemzsitink.milletguard.R
import kotlinx.coroutines.launch

private const val GMS = "com.google.android.gms"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedScreen(vm: GuardViewModel, state: GuardState, messenger: Messenger, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var key by rememberSaveable(state.settingsKey) { mutableStateOf(state.settingsKey) }
    var item by rememberSaveable(state.requiredItem) { mutableStateOf(state.requiredItem) }
    val dirty = key.trim() != state.settingsKey || item.trim() != state.requiredItem
    val valid = key.isNotBlank() && item.isNotBlank()
    val recovery = "adb shell pm enable " +
        "${context.packageName}/${LauncherAlias.COMPONENT.removePrefix(context.packageName)}"

    val copiedText = stringResource(R.string.sb_copied)
    val defaultKey = stringResource(R.string.default_key)
    val defaultItem = stringResource(R.string.default_required_item)

    fun copy(text: String) = scope.launch {
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("MilletGuard", text)))
        messenger.show(copiedText)
    }

    val mono = TextStyle(fontFamily = FontFamily.Monospace)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            contentPadding = pagePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Card(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) { Text(stringResource(R.string.adv_banner), modifier = Modifier.padding(16.dp)) }
            }
            item {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text(stringResource(R.string.adv_key)) },
                        singleLine = true,
                        textStyle = mono,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = item,
                        onValueChange = { item = it },
                        label = { Text(stringResource(R.string.adv_item)) },
                        singleLine = true,
                        textStyle = mono,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.saveAdvanced(key, item) },
                            enabled = dirty && valid,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.save)) }
                        TextButton(onClick = {
                            key = defaultKey
                            item = defaultItem
                        }) { Text(stringResource(R.string.reset)) }
                    }
                }
            }
            item {
                Row(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.current_list), Modifier.weight(1f))
                    IconButton(onClick = { copy(state.whitelistRaw.orEmpty()) }, enabled = !state.whitelistRaw.isNullOrEmpty()) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy))
                    }
                }
            }
            if (state.whitelist.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.list_empty),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(4.dp),
                    )
                }
            } else {
                items(state.whitelist.size, key = { "$it:${state.whitelist[it]}" }) { i ->
                    val pkg = state.whitelist[i]
                    Column(Modifier.widthIn(max = 600.dp).padding(bottom = ListItemDefaults.SegmentedGap)) {
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(i, state.whitelist.size),
                            colors = groupColors(),
                            trailingContent = when (pkg) {
                                GMS -> ({ Badge { Text(stringResource(R.string.badge_gms)) } })
                                context.packageName -> ({ Badge { Text(stringResource(R.string.badge_self)) } })
                                else -> null
                            },
                        ) { Text(pkg, style = MaterialTheme.typography.bodyMedium.merge(mono)) }
                    }
                }
            }
            item {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.recovery))
                    Text(stringResource(R.string.recover_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            SelectionContainer(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                Text(recovery, style = MaterialTheme.typography.bodySmall.merge(mono))
                            }
                            IconButton(onClick = { copy(recovery) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy))
                            }
                        }
                    }
                }
            }
        }
    }
}
