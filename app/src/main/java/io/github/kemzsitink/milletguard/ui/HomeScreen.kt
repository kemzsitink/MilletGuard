package io.github.kemzsitink.milletguard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.kemzsitink.milletguard.AutostartStatusReader
import io.github.kemzsitink.milletguard.R

private val SetupStep.title: Int
    get() = when (this) {
        SetupStep.Permission -> R.string.s1
        SetupStep.Protection -> R.string.s2
        SetupStep.Autostart -> R.string.s3
        SetupStep.Battery -> R.string.s4
    }

private val SetupStep.actionLabel: Int
    get() = when (this) {
        SetupStep.Permission -> R.string.btn_allow
        SetupStep.Protection -> R.string.btn_turn_on
        SetupStep.Autostart -> R.string.btn_open
        SetupStep.Battery -> R.string.btn_allow
    }

/** Runs the system / app action that completes a setup step. */
fun performStep(step: SetupStep, vm: GuardViewModel, context: android.content.Context, messenger: Messenger) {
    val notAvailable = { messenger.show(context.getString(R.string.not_available)) }
    when (step) {
        SetupStep.Permission -> {
            vm.markPermissionAttempted()
            if (!SystemIntents.openWriteSettings(context)) notAvailable()
        }
        SetupStep.Protection -> vm.turnOnProtection()
        SetupStep.Autostart -> if (!SystemIntents.openAutostart(context)) notAvailable()
        SetupStep.Battery -> if (!vm.allowSelfNoRestrict()) {
            if (!SystemIntents.openAppPermissions(context, context.packageName)) notAvailable()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: GuardViewModel, state: GuardState, messenger: Messenger) {
    var whyOpen by rememberSaveable { mutableStateOf(false) }
    var tileHelpOpen by rememberSaveable { mutableStateOf(false) }
    var setupExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        contentWindowInsets = WindowInsets(0),
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = pagePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "hero") {
                StatusHero(
                    state = state,
                    onPrimary = { heroPrimaryAction(state, vm, context, messenger) },
                    onWhy = { whyOpen = true },
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            }

            val allDone = state.doneCount == SetupStep.entries.size
            if (state.gmsInstalled) {
                item(key = "setup") {
                    SetupSection(
                        state = state,
                        expanded = !allDone || setupExpanded,
                        allDone = allDone,
                        onToggle = { setupExpanded = !setupExpanded },
                        onStep = { performStep(it, vm, context, messenger) },
                        onConfirmAutostart = vm::confirmAutostart,
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }

            if (state.health == Health.Protected && !state.autoHideSettled && !state.iconHidden) {
                item(key = "tidy") {
                    TidyUpCard(
                        tileAdded = state.tileAdded,
                        onAddTile = {
                            if (SystemIntents.canRequestTile) {
                                SystemIntents.requestAddTile(context) { added -> if (added) vm.markTileAdded() }
                            } else {
                                tileHelpOpen = true
                            }
                        },
                        onHide = { vm.setIconHidden(true) },
                        onNotNow = vm::settleAutoHide,
                        modifier = Modifier.widthIn(max = 600.dp).padding(top = 16.dp),
                    )
                }
            }

            if (state.canWrite) {
                item(key = "actions") {
                    QuickActions(
                        busy = state.busy,
                        onCheck = vm::checkNow,
                        onReconnect = vm::reconnect,
                        modifier = Modifier.widthIn(max = 600.dp).padding(top = 16.dp),
                    )
                }
            }

            if (state.iconHidden) {
                item(key = "hidden") {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                        // The action sits under the text so the explanation keeps the full width.
                        supportingContent = {
                            TextButton(onClick = { vm.setIconHidden(false) }) {
                                Text(stringResource(R.string.show_icon))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.widthIn(max = 600.dp).padding(top = 8.dp),
                    ) { Text(stringResource(R.string.icon_hidden_note)) }
                }
            }
        }
    }

    if (whyOpen) {
        ModalBottomSheet(onDismissRequest = { whyOpen = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(
                    stringResource(R.string.why_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.why_body), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(20.dp))
                Button(
                    onClick = { whyOpen = false; vm.fixNow() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.h_btn_retry)) }
                state.rejectedMessage?.let {
                    Spacer(Modifier.size(16.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (tileHelpOpen) TileHelpSheet(onDismiss = { tileHelpOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.tile), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.tile_manual), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(20.dp))
            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

private fun heroPrimaryAction(state: GuardState, vm: GuardViewModel, context: android.content.Context, messenger: Messenger) {
    when (state.health) {
        Health.NoPermission -> performStep(SetupStep.Permission, vm, context, messenger)
        Health.Blocked, Health.Rejected -> vm.fixNow()
        Health.Unguarded -> vm.turnOnProtection()
        Health.Caveat -> {
            val next = state.nextStep
            if (next != null) {
                performStep(next, vm, context, messenger)
            } else if (!SystemIntents.openNotificationSettings(context)) {
                messenger.show(context.getString(R.string.not_available))
            }
        }
        Health.Protected, Health.GmsMissing -> Unit
    }
}

private data class HeroLook(
    val shape: androidx.graphics.shapes.RoundedPolygon,
    val icon: ImageVector,
    val container: Color,
    val onContainer: Color,
    val badge: Color,
    val onBadge: Color,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun heroLook(health: Health): HeroLook {
    val c = MaterialTheme.colorScheme
    return when (health) {
        Health.Protected -> HeroLook(MaterialShapes.Cookie9Sided, Icons.Rounded.VerifiedUser, c.primaryContainer, c.onPrimaryContainer, c.primary, c.onPrimary)
        Health.Caveat, Health.Unguarded -> HeroLook(MaterialShapes.Cookie4Sided, Icons.Rounded.Shield, c.tertiaryContainer, c.onTertiaryContainer, c.tertiary, c.onTertiary)
        Health.Blocked -> HeroLook(MaterialShapes.SoftBurst, Icons.Rounded.NotificationsOff, c.errorContainer, c.onErrorContainer, c.error, c.onError)
        Health.Rejected, Health.GmsMissing -> HeroLook(MaterialShapes.SoftBurst, Icons.Rounded.GppMaybe, c.errorContainer, c.onErrorContainer, c.error, c.onError)
        Health.NoPermission -> HeroLook(MaterialShapes.Circle, Icons.Rounded.PlayArrow, c.surfaceContainerHigh, c.onSurface, c.primary, c.onPrimary)
    }
}

@Composable
private fun heroText(state: GuardState): Pair<String, String> = when (state.health) {
    Health.GmsMissing -> stringResource(R.string.h_no_gms) to stringResource(R.string.h_no_gms_sub)
    Health.NoPermission -> stringResource(R.string.h_setup) to stringResource(
        if (state.permissionAttempted) R.string.s1_retry else R.string.h_setup_sub,
    )
    Health.Rejected -> stringResource(R.string.h_rejected) to stringResource(R.string.h_rejected_sub)
    Health.Blocked -> stringResource(R.string.h_blocked) to stringResource(R.string.h_blocked_sub)
    Health.Unguarded -> stringResource(R.string.h_unguarded) to stringResource(R.string.h_unguarded_sub)
    Health.Caveat -> stringResource(R.string.h_caveat) to when (state.nextStep) {
        SetupStep.Autostart -> stringResource(R.string.s3_sub)
        SetupStep.Battery -> stringResource(R.string.s4_sub)
        else -> stringResource(R.string.h_caveat_notif_sub)
    }
    Health.Protected -> stringResource(R.string.h_protected) to stringResource(R.string.h_protected_sub)
}

@Composable
private fun heroButton(state: GuardState): String? = when (state.health) {
    Health.NoPermission -> stringResource(R.string.h_btn_start)
    Health.Blocked -> stringResource(R.string.h_btn_fix)
    Health.Rejected -> stringResource(R.string.h_btn_retry)
    Health.Unguarded -> stringResource(R.string.h_btn_turn_on)
    Health.Caveat -> stringResource(
        R.string.h_btn_next,
        stringResource(state.nextStep?.title ?: R.string.notif),
    )
    Health.Protected, Health.GmsMissing -> null
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusHero(state: GuardState, onPrimary: () -> Unit, onWhy: () -> Unit, modifier: Modifier = Modifier) {
    val health = state.health
    val look = heroLook(health)
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(look.container, motion.defaultEffectsSpec(), label = "heroContainer")
    val onContainer by animateColorAsState(look.onContainer, motion.defaultEffectsSpec(), label = "heroContent")
    val haptics = LocalHapticFeedback.current

    // Celebrate reaching Protected only on a real transition, never on first composition.
    var previous by remember { mutableStateOf(health) }
    LaunchedEffect(health) {
        if (health != previous && health == Health.Protected) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        previous = health
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
    ) {
        Column(Modifier.padding(24.dp)) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (state.busy) {
                    ContainedLoadingIndicator(Modifier.size(72.dp))
                } else {
                    AnimatedContent(
                        targetState = health,
                        transitionSpec = {
                            (scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f) + fadeIn(motion.defaultEffectsSpec()))
                                .togetherWith(fadeOut(motion.fastEffectsSpec()))
                        },
                        label = "heroShape",
                    ) { h ->
                        val l = heroLook(h)
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(l.shape.toShape())
                                .background(l.badge),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(l.icon, contentDescription = null, tint = l.onBadge, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.size(20.dp))
            val (title, body) = heroText(state)
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    heading()
                },
            ) {
                Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
                Spacer(Modifier.size(8.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
            heroButton(state)?.let { label ->
                Spacer(Modifier.size(20.dp))
                Button(
                    onClick = onPrimary,
                    enabled = !state.busy,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                ) {
                    Text(label, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
            if (health == Health.Rejected) {
                TextButton(onClick = onWhy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.h_btn_why))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SetupSection(
    state: GuardState,
    expanded: Boolean,
    allDone: Boolean,
    onToggle: () -> Unit,
    onStep: (SetupStep) -> Unit,
    onConfirmAutostart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val steps = SetupStep.entries
    val total = steps.size
    val progressText = stringResource(R.string.setup_progress, state.doneCount, total)

    Column(modifier.fillMaxWidth().animateContentSize(motion.defaultSpatialSpec())) {
        if (allDone) {
            ListItem(
                onClick = onToggle,
                leadingContent = {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(top = 12.dp),
            ) { Text(stringResource(R.string.setup_complete)) }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(progressText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearWavyProgressIndicator(
                progress = { state.doneCount / total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 12.dp)
                    .semantics { stateDescription = progressText },
            )
        }

        if (!expanded) return@Column
        if (allDone) Spacer(Modifier.size(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            steps.forEachIndexed { index, step ->
                val done = state.isDone(step)
                val current = step == state.nextStep
                val future = !done && !current
                val title = stringResource(step.title)
                val cd = stringResource(
                    R.string.step_cd, index + 1, total, title,
                    stringResource(if (done) R.string.step_state_done else R.string.step_state_todo),
                )
                val autostartUnknown = step == SetupStep.Autostart &&
                    state.selfAutostart == AutostartStatusReader.Status.UNKNOWN
                val supporting = when {
                    future -> stringResource(R.string.after_step, index)
                    step == SetupStep.Permission && state.permissionAttempted && !done -> stringResource(R.string.s1_retry)
                    step == SetupStep.Permission -> stringResource(R.string.s1_sub)
                    step == SetupStep.Protection -> stringResource(R.string.s2_sub)
                    step == SetupStep.Autostart && autostartUnknown && !done -> stringResource(R.string.s3_unknown)
                    step == SetupStep.Autostart -> stringResource(R.string.s3_sub)
                    else -> stringResource(R.string.s4_sub)
                }

                SegmentedListItem(
                    onClick = { if (!done) onStep(step) },
                    // Done rows stay readable (no disabled tint) but expose no click action.
                    enabled = (!future && !state.busy) || done,
                    shapes = ListItemDefaults.segmentedShapes(index, total),
                    colors = groupColors(),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        // Keep the merged title and guidance; only add the step position and state.
                        stateDescription = cd
                        if (done) onClick(label = null, action = null)
                    },
                    leadingContent = { StepBadge(number = index + 1, done = done) },
                    trailingContent = if (done) {
                        { Text(stringResource(R.string.done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                    } else {
                        null
                    },
                    supportingContent = {
                        Column {
                            Text(supporting)
                            if (current) {
                                FlowRow(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilledTonalButton(onClick = { onStep(step) }, enabled = !state.busy, shapes = ButtonDefaults.shapes()) {
                                        Text(stringResource(step.actionLabel))
                                    }
                                    if (autostartUnknown) {
                                        TextButton(onClick = onConfirmAutostart) { Text(stringResource(R.string.btn_did_it)) }
                                    }
                                }
                            }
                        }
                    },
                ) { Text(title) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepBadge(number: Int, done: Boolean) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(MaterialShapes.Circle.toShape())
            .background(if (done) c.primary else c.surfaceContainerHighest)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onPrimary)
        } else {
            Text("$number", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TidyUpCard(
    tileAdded: Boolean,
    onAddTile: () -> Unit,
    onHide: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(if (tileAdded) R.string.hide_icon else R.string.tidy_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(if (tileAdded) R.string.hide_icon_warn else R.string.tidy_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (tileAdded) onHide else onAddTile, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(if (tileAdded) R.string.hide_icon_btn else R.string.add_tile))
                }
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now)) }
            }
        }
    }
}

@Composable
private fun QuickActions(busy: Boolean, onCheck: () -> Unit, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        val check: @Composable (Modifier) -> Unit = { m ->
            FilledTonalButton(onClick = onCheck, enabled = !busy, shapes = ButtonDefaults.shapes(), modifier = m.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.check_now))
            }
        }
        val reconnect: @Composable (Modifier) -> Unit = { m ->
            FilledTonalButton(onClick = onReconnect, shapes = ButtonDefaults.shapes(), modifier = m.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.reconnect))
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                check(Modifier.fillMaxWidth())
                reconnect(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                check(Modifier.weight(1f))
                reconnect(Modifier.weight(1f))
            }
        }
    }
}
