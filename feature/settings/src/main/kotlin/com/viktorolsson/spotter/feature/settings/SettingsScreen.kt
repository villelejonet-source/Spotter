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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val planName by viewModel.planName.collectAsStateWithLifecycle()
    SettingsScreen(
        preferences = preferences,
        planName = planName,
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
        modifier = Modifier.padding(top = 8.dp),
    )
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
        SettingsScreen(UserPreferences(), "Upper / Lower · 4 days", {}, {}, {}, {}, {}, {}, {})
    }
}
