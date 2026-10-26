package com.viktorolsson.spotter.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.data.sync.SyncError
import com.viktorolsson.spotter.core.data.sync.SyncStatus
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.ThemeMode
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.ui.component.ConfirmDialog
import com.viktorolsson.spotter.core.ui.component.RestTimePickerDialog
import com.viktorolsson.spotter.core.ui.component.SectionHeader
import com.viktorolsson.spotter.core.ui.component.SegmentedChoice
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatShortDateTime
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.theme.supportsDynamicColor
import com.viktorolsson.spotter.feature.settings.resources.Res
import com.viktorolsson.spotter.feature.settings.resources.backup_change_password
import com.viktorolsson.spotter.feature.settings.resources.backup_delete
import com.viktorolsson.spotter.feature.settings.resources.backup_delete_body
import com.viktorolsson.spotter.feature.settings.resources.backup_delete_confirm
import com.viktorolsson.spotter.feature.settings.resources.backup_delete_title
import com.viktorolsson.spotter.feature.settings.resources.backup_error_failed
import com.viktorolsson.spotter.feature.settings.resources.backup_error_offline
import com.viktorolsson.spotter.feature.settings.resources.backup_intro
import com.viktorolsson.spotter.feature.settings.resources.backup_last_synced
import com.viktorolsson.spotter.feature.settings.resources.backup_never_synced
import com.viktorolsson.spotter.feature.settings.resources.backup_section
import com.viktorolsson.spotter.feature.settings.resources.backup_sign_in
import com.viktorolsson.spotter.feature.settings.resources.backup_sign_in_existing
import com.viktorolsson.spotter.feature.settings.resources.backup_sign_out
import com.viktorolsson.spotter.feature.settings.resources.backup_sign_out_body
import com.viktorolsson.spotter.feature.settings.resources.backup_sign_out_title
import com.viktorolsson.spotter.feature.settings.resources.backup_signed_in_as
import com.viktorolsson.spotter.feature.settings.resources.backup_sync_now
import com.viktorolsson.spotter.feature.settings.resources.backup_syncing
import com.viktorolsson.spotter.feature.settings.resources.profile_title
import com.viktorolsson.spotter.feature.settings.resources.settings_build_plan
import com.viktorolsson.spotter.feature.settings.resources.settings_default_rest
import com.viktorolsson.spotter.feature.settings.resources.settings_default_rest_change
import com.viktorolsson.spotter.feature.settings.resources.settings_default_rest_summary
import com.viktorolsson.spotter.feature.settings.resources.settings_dynamic_color
import com.viktorolsson.spotter.feature.settings.resources.settings_dynamic_color_summary
import com.viktorolsson.spotter.feature.settings.resources.settings_effort_off
import com.viktorolsson.spotter.feature.settings.resources.settings_effort_off_summary
import com.viktorolsson.spotter.feature.settings.resources.settings_effort_rir
import com.viktorolsson.spotter.feature.settings.resources.settings_effort_rir_summary
import com.viktorolsson.spotter.feature.settings.resources.settings_effort_rpe
import com.viktorolsson.spotter.feature.settings.resources.settings_effort_rpe_summary
import com.viktorolsson.spotter.feature.settings.resources.settings_effort_scale
import com.viktorolsson.spotter.feature.settings.resources.settings_no_plan
import com.viktorolsson.spotter.feature.settings.resources.settings_privacy_note
import com.viktorolsson.spotter.feature.settings.resources.settings_rebuild_plan
import com.viktorolsson.spotter.feature.settings.resources.settings_rebuild_summary
import com.viktorolsson.spotter.feature.settings.resources.settings_section_appearance
import com.viktorolsson.spotter.feature.settings.resources.settings_section_plan
import com.viktorolsson.spotter.feature.settings.resources.settings_section_units
import com.viktorolsson.spotter.feature.settings.resources.settings_section_workout
import com.viktorolsson.spotter.feature.settings.resources.settings_theme
import com.viktorolsson.spotter.feature.settings.resources.settings_theme_dark
import com.viktorolsson.spotter.feature.settings.resources.settings_theme_light
import com.viktorolsson.spotter.feature.settings.resources.settings_theme_system
import com.viktorolsson.spotter.feature.settings.resources.settings_unit_kg
import com.viktorolsson.spotter.feature.settings.resources.settings_unit_lb
import com.viktorolsson.spotter.feature.settings.resources.settings_view_plan
import com.viktorolsson.spotter.feature.settings.resources.settings_weight_unit

@Composable
internal fun SettingsRoute(
    onOpenPlan: () -> Unit,
    onRebuildPlan: () -> Unit,
    onOpenAccount: (AccountRoute) -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val planName by viewModel.planName.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(getString(text))
        viewModel.messageShown()
    }
    SettingsScreen(
        preferences = preferences,
        planName = planName,
        syncStatus = syncStatus,
        sync = SyncActions(
            onSignIn = { onOpenAccount(AccountRoute(newAccount = it)) },
            onChangePassword = { onOpenAccount(AccountRoute(newPassword = true)) },
            onSyncNow = viewModel::syncNow,
            onSignOut = viewModel::signOut,
            onDeleteAccount = viewModel::deleteAccount,
        ),
        onOpenPlan = onOpenPlan,
        onRebuildPlan = onRebuildPlan,
        onThemeModeChange = { viewModel.setThemeMode(it) },
        onDynamicColorChange = { viewModel.setDynamicColor(it) },
        onWeightUnitChange = { viewModel.setWeightUnit(it) },
        onDefaultRestChange = { viewModel.setDefaultRest(it) },
        onEffortScaleChange = { viewModel.setEffortScale(it) },
        snackbarHostState = snackbar,
    )
}

@Composable
internal fun SettingsScreen(
    preferences: UserPreferences,
    planName: String?,
    syncStatus: SyncStatus,
    sync: SyncActions,
    onOpenPlan: () -> Unit,
    onRebuildPlan: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onWeightUnitChange: (WeightUnit) -> Unit,
    onDefaultRestChange: (Int) -> Unit,
    onEffortScaleChange: (EffortScale) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var pickingRest by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { SpotterTopBar(title = stringResource(Res.string.profile_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
        ) {
            SectionHeader(stringResource(Res.string.settings_section_plan), Modifier.padding(top = Spacing.sm))
            Text(
                planName ?: stringResource(Res.string.settings_no_plan),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(Res.string.settings_rebuild_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (planName != null) {
                    OutlinedButton(onClick = onOpenPlan) { Text(stringResource(Res.string.settings_view_plan)) }
                    OutlinedButton(onClick = onRebuildPlan) { Text(stringResource(Res.string.settings_rebuild_plan)) }
                } else {
                    Button(onClick = onRebuildPlan) { Text(stringResource(Res.string.settings_build_plan)) }
                }
            }

            if (syncStatus.available) BackupSection(syncStatus, sync)

            SectionHeader(stringResource(Res.string.settings_section_appearance), Modifier.padding(top = Spacing.sm))
            Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            SegmentedChoice(
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(Res.string.settings_theme_system),
                    ThemeMode.LIGHT to stringResource(Res.string.settings_theme_light),
                    ThemeMode.DARK to stringResource(Res.string.settings_theme_dark),
                ),
                selected = preferences.themeMode,
                onSelect = onThemeModeChange,
            )
            if (supportsDynamicColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.settings_dynamic_color), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.settings_dynamic_color_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = preferences.dynamicColor, onCheckedChange = onDynamicColorChange)
                }
            }

            SectionHeader(stringResource(Res.string.settings_section_units), Modifier.padding(top = Spacing.sm))
            Text(stringResource(Res.string.settings_weight_unit), style = MaterialTheme.typography.bodyLarge)
            SegmentedChoice(
                options = listOf(
                    WeightUnit.KG to stringResource(Res.string.settings_unit_kg),
                    WeightUnit.LB to stringResource(Res.string.settings_unit_lb),
                ),
                selected = preferences.weightUnit,
                onSelect = onWeightUnitChange,
            )

            SectionHeader(stringResource(Res.string.settings_section_workout), Modifier.padding(top = Spacing.sm))
            SettingRow(
                title = stringResource(Res.string.settings_default_rest),
                summary = stringResource(Res.string.settings_default_rest_summary),
                modifier = Modifier.clickable(onClickLabel = stringResource(Res.string.settings_default_rest_change), role = Role.Button) { pickingRest = true },
            ) {
                Text(formatClock(preferences.defaultRestSeconds.toLong()), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(Res.string.settings_effort_scale), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    when (preferences.effortScale) {
                        EffortScale.OFF -> Res.string.settings_effort_off_summary
                        EffortScale.RIR -> Res.string.settings_effort_rir_summary
                        EffortScale.RPE -> Res.string.settings_effort_rpe_summary
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedChoice(
                options = listOf(
                    EffortScale.OFF to stringResource(Res.string.settings_effort_off),
                    EffortScale.RIR to stringResource(Res.string.settings_effort_rir),
                    EffortScale.RPE to stringResource(Res.string.settings_effort_rpe),
                ),
                selected = preferences.effortScale,
                onSelect = onEffortScaleChange,
            )

            Text(
                stringResource(Res.string.settings_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }
    if (pickingRest) {
        RestTimePickerDialog(
            title = stringResource(Res.string.settings_default_rest),
            selectedSeconds = preferences.defaultRestSeconds,
            onSelect = { onDefaultRestChange(it); pickingRest = false },
            onDismiss = { pickingRest = false },
        )
    }
}

internal class SyncActions(
    /** Opens sign-in; true for creating a new account. */
    val onSignIn: (newAccount: Boolean) -> Unit,
    /** Also how an account made with email codes gets its first password. */
    val onChangePassword: () -> Unit,
    val onSyncNow: () -> Unit,
    val onSignOut: () -> Unit,
    val onDeleteAccount: () -> Unit,
)

@Composable
private fun BackupSection(status: SyncStatus, actions: SyncActions) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    SectionHeader(stringResource(Res.string.backup_section), Modifier.padding(top = Spacing.sm))
    val account = status.account
    if (account == null) {
        Text(
            stringResource(Res.string.backup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { actions.onSignIn(true) }) { Text(stringResource(Res.string.backup_sign_in)) }
        TextButton(onClick = { actions.onSignIn(false) }) { Text(stringResource(Res.string.backup_sign_in_existing)) }
        return
    }
    Text(stringResource(Res.string.backup_signed_in_as, account.email.orEmpty()), style = MaterialTheme.typography.bodyLarge)
    val lastSynced = status.lastSyncedAt
    Text(
        when {
            status.syncing -> stringResource(Res.string.backup_syncing)
            status.error == SyncError.OFFLINE -> stringResource(Res.string.backup_error_offline)
            status.error == SyncError.FAILED -> stringResource(Res.string.backup_error_failed)
            lastSynced != null -> stringResource(Res.string.backup_last_synced, formatShortDateTime(lastSynced))
            else -> stringResource(Res.string.backup_never_synced)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (status.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = actions.onSyncNow, enabled = !status.syncing) { Text(stringResource(Res.string.backup_sync_now)) }
        OutlinedButton(onClick = { confirmSignOut = true }) { Text(stringResource(Res.string.backup_sign_out)) }
    }
    TextButton(onClick = actions.onChangePassword) { Text(stringResource(Res.string.backup_change_password)) }
    TextButton(onClick = { confirmDelete = true }) {
        Text(stringResource(Res.string.backup_delete), color = MaterialTheme.colorScheme.error)
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(Res.string.backup_sign_out_title),
            text = stringResource(Res.string.backup_sign_out_body),
            confirmText = stringResource(Res.string.backup_sign_out),
            onConfirm = { confirmSignOut = false; actions.onSignOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.backup_delete_title),
            text = stringResource(Res.string.backup_delete_body),
            confirmText = stringResource(Res.string.backup_delete_confirm),
            onConfirm = { confirmDelete = false; actions.onDeleteAccount() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.lg)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    SpotterTheme {
        SettingsScreen(
            UserPreferences(), "Upper / Lower · 4 days", SyncStatus(), SyncActions({}, {}, {}, {}, {}),
            {}, {}, {}, {}, {}, {}, {},
        )
    }
}
