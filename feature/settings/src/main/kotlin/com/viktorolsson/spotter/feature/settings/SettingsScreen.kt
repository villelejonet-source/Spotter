package com.viktorolsson.spotter.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.viktorolsson.spotter.core.data.sync.SyncError
import com.viktorolsson.spotter.core.data.sync.SyncStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import com.viktorolsson.spotter.core.ui.component.RestTimePickerDialog
import com.viktorolsson.spotter.core.ui.formatClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.ThemeMode
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.theme.supportsDynamicColor

@Composable
internal fun SettingsRoute(
    onOpenPlan: () -> Unit,
    onRebuildPlan: () -> Unit,
    onOpenAccount: (newAccount: Boolean) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val planName by viewModel.planName.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    SettingsScreen(
        preferences = preferences,
        planName = planName,
        syncStatus = syncStatus,
        sync = SyncActions(onOpenAccount, viewModel::syncNow, viewModel::signOut, viewModel::deleteAccount),
        onOpenPlan = onOpenPlan,
        onRebuildPlan = onRebuildPlan,
        onThemeModeChange = { viewModel.setThemeMode(it) },
        onDynamicColorChange = { viewModel.setDynamicColor(it) },
        onWeightUnitChange = { viewModel.setWeightUnit(it) },
        onDefaultRestChange = { viewModel.setDefaultRest(it) },
        onLogRirChange = { viewModel.setLogRir(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onLogRirChange: (Boolean) -> Unit,
) {
    var pickingRest by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(R.string.settings_section_plan)
            Text(
                planName ?: stringResource(R.string.settings_no_plan),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.settings_rebuild_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (planName != null) {
                    OutlinedButton(onClick = onOpenPlan) { Text(stringResource(R.string.settings_view_plan)) }
                    OutlinedButton(onClick = onRebuildPlan) { Text(stringResource(R.string.settings_rebuild_plan)) }
                } else {
                    Button(onClick = onRebuildPlan) { Text(stringResource(R.string.settings_build_plan)) }
                }
            }

            if (syncStatus.available) BackupSection(syncStatus, sync)

            SectionHeader(R.string.settings_section_appearance)
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            SegmentedChoice(
                options = listOf(
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark,
                ),
                selected = preferences.themeMode,
                onSelect = onThemeModeChange,
            )
            if (supportsDynamicColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_dynamic_color_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = preferences.dynamicColor, onCheckedChange = onDynamicColorChange)
                }
            }

            SectionHeader(R.string.settings_section_units)
            Text(stringResource(R.string.settings_weight_unit), style = MaterialTheme.typography.bodyLarge)
            SegmentedChoice(
                options = listOf(
                    WeightUnit.KG to R.string.settings_unit_kg,
                    WeightUnit.LB to R.string.settings_unit_lb,
                ),
                selected = preferences.weightUnit,
                onSelect = onWeightUnitChange,
            )

            SectionHeader(R.string.settings_section_workout)
            SettingRow(
                title = stringResource(R.string.settings_default_rest),
                summary = stringResource(R.string.settings_default_rest_summary),
                modifier = Modifier.clickable { pickingRest = true },
            ) {
                Text(formatClock(preferences.defaultRestSeconds.toLong()), style = MaterialTheme.typography.titleMedium)
            }
            SettingRow(
                title = stringResource(R.string.settings_log_rir),
                summary = stringResource(R.string.settings_log_rir_summary),
            ) {
                Switch(checked = preferences.logRir, onCheckedChange = onLogRirChange)
            }

            Text(
                stringResource(R.string.settings_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
    if (pickingRest) {
        RestTimePickerDialog(
            title = stringResource(R.string.settings_default_rest),
            selectedSeconds = preferences.defaultRestSeconds,
            onSelect = { onDefaultRestChange(it); pickingRest = false },
            onDismiss = { pickingRest = false },
        )
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

internal class SyncActions(
    /** Opens sign-in; true for creating a new account. */
    val onSignIn: (newAccount: Boolean) -> Unit,
    val onSyncNow: () -> Unit,
    val onSignOut: () -> Unit,
    val onDeleteAccount: () -> Unit,
)

@Composable
private fun BackupSection(status: SyncStatus, actions: SyncActions) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.backup_section)
    val account = status.account
    if (account == null) {
        Text(
            stringResource(R.string.backup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { actions.onSignIn(true) }) { Text(stringResource(R.string.backup_sign_in)) }
        TextButton(onClick = { actions.onSignIn(false) }) { Text(stringResource(R.string.backup_sign_in_existing)) }
        return
    }
    Text(stringResource(R.string.backup_signed_in_as, account.email.orEmpty()), style = MaterialTheme.typography.bodyLarge)
    Text(
        when {
            status.syncing -> stringResource(R.string.backup_syncing)
            status.error == SyncError.OFFLINE -> stringResource(R.string.backup_error_offline)
            status.error == SyncError.FAILED -> stringResource(R.string.backup_error_failed)
            status.lastSyncedAt != null -> stringResource(
                R.string.backup_last_synced,
                DateTimeFormatter.ofPattern("EEE d MMM HH:mm").format(status.lastSyncedAt!!.atZone(ZoneId.systemDefault())),
            )
            else -> stringResource(R.string.backup_never_synced)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (status.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onSyncNow, enabled = !status.syncing) { Text(stringResource(R.string.backup_sync_now)) }
        OutlinedButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.backup_sign_out)) }
    }
    TextButton(onClick = { confirmDelete = true }) {
        Text(stringResource(R.string.backup_delete), color = MaterialTheme.colorScheme.error)
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.backup_sign_out_title)) },
            text = { Text(stringResource(R.string.backup_sign_out_body)) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; actions.onSignOut() }) { Text(stringResource(R.string.backup_sign_out)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.backup_delete_title)) },
            text = { Text(stringResource(R.string.backup_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDeleteAccount() }) {
                    Text(stringResource(R.string.backup_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) } },
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
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun <T> SegmentedChoice(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    SpotterTheme {
        SettingsScreen(
            UserPreferences(), "Upper / Lower · 4 days", SyncStatus(), SyncActions({}, {}, {}, {}),
            {}, {}, {}, {}, {}, {}, {},
        )
    }
}
