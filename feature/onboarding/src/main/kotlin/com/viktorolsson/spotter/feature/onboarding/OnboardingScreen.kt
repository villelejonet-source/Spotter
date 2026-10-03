package com.viktorolsson.spotter.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.labelRes

@Composable
internal fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler { if (!viewModel.back()) onDone() }
    OnboardingScreen(
        state = state,
        onBack = { if (!viewModel.back()) onDone() },
        onSkip = { viewModel.skip(onDone) },
        onNext = viewModel::next,
        onUpdate = viewModel::update,
        onChoose = viewModel::choose,
        onImperial = viewModel::setImperial,
        onSplit = viewModel::setSplit,
        onFinish = { viewModel.finish(onDone) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onUpdate: ((Answers) -> Answers) -> Unit,
    onChoose: ((Answers) -> Answers) -> Unit,
    onImperial: (Boolean) -> Unit,
    onSplit: (com.viktorolsson.spotter.core.model.SplitType?) -> Unit,
    onFinish: () -> Unit,
) {
    val answers = state.answers
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.onb_back))
                        }
                    },
                    actions = {
                        if (!state.rebuild && state.step == Step.BODY) {
                            TextButton(onClick = onSkip) { Text(stringResource(R.string.onb_skip)) }
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { (state.step.ordinal + 1f) / Step.entries.size },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
        },
        bottomBar = {
            val (label, action, enabled) = when (state.step) {
                Step.LIFTS -> Triple(R.string.onb_build, onNext, true)
                Step.SUMMARY -> Triple(R.string.onb_start, onFinish, state.generated != null && !state.saving)
                else -> Triple(R.string.onb_next, onNext, answers.isValid(state.step))
            }
            Button(
                onClick = action,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).height(56.dp),
            ) { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) }
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "step",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { step ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    Step.BODY -> BodyStep(answers, onUpdate, onImperial)
                    Step.GOAL -> {
                        Title(R.string.onb_goal_title)
                        listOf(
                            Triple(Goal.STRENGTH, R.string.onb_goal_strength, R.string.onb_goal_strength_sub),
                            Triple(Goal.HYPERTROPHY, R.string.onb_goal_hypertrophy, R.string.onb_goal_hypertrophy_sub),
                            Triple(Goal.GENERAL_FITNESS, R.string.onb_goal_general, R.string.onb_goal_general_sub),
                            Triple(Goal.FAT_LOSS, R.string.onb_goal_fat_loss, R.string.onb_goal_fat_loss_sub),
                        ).forEach { (goal, title, sub) ->
                            OptionCard(stringResource(title), answers.goal == goal, { onChoose { it.copy(goal = goal) } }, stringResource(sub))
                        }
                    }
                    Step.EXPERIENCE -> {
                        Title(R.string.onb_exp_title)
                        listOf(
                            Triple(ExperienceLevel.NEW, R.string.onb_exp_new, R.string.onb_exp_new_sub),
                            Triple(ExperienceLevel.INTERMEDIATE, R.string.onb_exp_intermediate, R.string.onb_exp_intermediate_sub),
                            Triple(ExperienceLevel.ADVANCED, R.string.onb_exp_advanced, R.string.onb_exp_advanced_sub),
                        ).forEach { (level, title, sub) ->
                            OptionCard(stringResource(title), answers.experience == level, { onChoose { it.copy(experience = level) } }, stringResource(sub))
                        }
                    }
                    Step.DAYS -> {
                        Title(R.string.onb_days_title)
                        (2..6).forEach { days ->
                            OptionCard(stringResource(R.string.onb_days_hint, days), answers.days == days, { onChoose { it.copy(days = days) } })
                        }
                    }
                    Step.LENGTH -> {
                        Title(R.string.onb_length_title)
                        listOf(30, 45, 60, 75).forEach { minutes ->
                            val label = if (minutes == 75) stringResource(R.string.onb_length_long) else stringResource(R.string.onb_length_option, minutes)
                            OptionCard(label, answers.minutes == minutes, { onChoose { it.copy(minutes = minutes) } })
                        }
                    }
                    Step.EQUIPMENT -> EquipmentStep(answers, onUpdate)
                    Step.FOCUS -> {
                        Title(R.string.onb_focus_title, R.string.onb_focus_subtitle)
                        ChipGroup(BodyArea.entries, answers.focus, { stringResource(it.labelRes) }) { area ->
                            onUpdate { it.copy(focus = it.focus.toggle(area), focusTouched = true) }
                        }
                    }
                    Step.LIMITATIONS -> {
                        Title(R.string.onb_limits_title, R.string.onb_limits_subtitle)
                        listOf(
                            Limitation.SHOULDER to R.string.onb_limit_shoulder,
                            Limitation.KNEE to R.string.onb_limit_knee,
                            Limitation.LOWER_BACK to R.string.onb_limit_lower_back,
                            Limitation.WRIST to R.string.onb_limit_wrist,
                        ).forEach { (limitation, label) ->
                            OptionCard(stringResource(label), limitation in answers.limitations, {
                                onUpdate { it.copy(limitations = it.limitations.toggle(limitation)) }
                            })
                        }
                        Text(
                            stringResource(R.string.onb_limits_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Step.LIFTS -> LiftsStep(answers, onUpdate)
                    Step.SUMMARY -> SummaryStep(state, onSplit)
                }
            }
        }
    }
}

@Composable
internal fun Title(title: Int, subtitle: Int? = null) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (subtitle != null) {
        Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BodyStep(answers: Answers, onUpdate: ((Answers) -> Answers) -> Unit, onImperial: (Boolean) -> Unit) {
    Title(R.string.onb_body_title, R.string.onb_body_subtitle)
    Text(stringResource(R.string.onb_sex), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Segmented(
        listOf(Sex.FEMALE to R.string.onb_sex_female, Sex.MALE to R.string.onb_sex_male),
        answers.sex,
    ) { sex -> onUpdate { it.copy(sex = sex) } }
    Segmented(
        listOf(false to R.string.onb_units_metric, true to R.string.onb_units_imperial),
        answers.imperial,
        onImperial,
    )
    NumberInput(answers.age, { v -> onUpdate { it.copy(age = v) } }, stringResource(R.string.onb_age), Modifier.fillMaxWidth())
    if (answers.imperial) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberInput(answers.heightFt, { v -> onUpdate { it.copy(heightFt = v) } }, stringResource(R.string.onb_height_ft), Modifier.weight(1f))
            NumberInput(answers.heightIn, { v -> onUpdate { it.copy(heightIn = v) } }, stringResource(R.string.onb_height_in), Modifier.weight(1f))
        }
    } else {
        NumberInput(answers.heightCm, { v -> onUpdate { it.copy(heightCm = v) } }, stringResource(R.string.onb_height_cm), Modifier.fillMaxWidth())
    }
    NumberInput(
        answers.weight,
        { v -> onUpdate { it.copy(weight = v) } },
        stringResource(R.string.onb_weight, answers.unit.label),
        Modifier.fillMaxWidth(),
        decimal = true,
    )
}

@Composable
private fun EquipmentStep(answers: Answers, onUpdate: ((Answers) -> Answers) -> Unit) {
    Title(R.string.onb_equipment_title)
    listOf(
        Triple(EquipmentPreset.FULL_GYM, R.string.onb_equipment_full, R.string.onb_equipment_full_sub),
        Triple(EquipmentPreset.HOME_GYM, R.string.onb_equipment_home, R.string.onb_equipment_home_sub),
        Triple(EquipmentPreset.DUMBBELLS, R.string.onb_equipment_dumbbells, R.string.onb_equipment_dumbbells_sub),
        Triple(EquipmentPreset.BODYWEIGHT, R.string.onb_equipment_bodyweight, R.string.onb_equipment_bodyweight_sub),
    ).forEach { (preset, title, sub) ->
        OptionCard(stringResource(title), answers.equipment == preset.equipment, {
            onUpdate { it.copy(equipment = preset.equipment) }
        }, stringResource(sub))
    }
    Text(stringResource(R.string.onb_equipment_items), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    ChipGroup(Equipment.entries, answers.equipment, { stringResource(it.labelRes) }) { item ->
        onUpdate { it.copy(equipment = it.equipment.toggle(item)) }
    }
}

@Composable
private fun LiftsStep(answers: Answers, onUpdate: ((Answers) -> Answers) -> Unit) {
    Title(R.string.onb_lifts_title, R.string.onb_lifts_subtitle)
    listOf(
        Lift.BENCH to R.string.onb_lift_bench,
        Lift.SQUAT to R.string.onb_lift_squat,
        Lift.DEADLIFT to R.string.onb_lift_deadlift,
    ).forEach { (lift, label) ->
        val input = answers.lifts[lift] ?: LiftInput()
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberInput(
                input.weight,
                { v -> onUpdate { it.copy(lifts = it.lifts + (lift to input.copy(weight = v))) } },
                stringResource(R.string.onb_lift_weight, answers.unit.label),
                Modifier.weight(1f),
                decimal = true,
            )
            NumberInput(
                input.reps,
                { v -> onUpdate { it.copy(lifts = it.lifts + (lift to input.copy(reps = v))) } },
                stringResource(R.string.onb_lift_reps),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun <T> Segmented(options: List<Pair<T, Int>>, selected: T?, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(options: List<T>, selected: Set<T>, label: @Composable (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option in selected, onClick = { onToggle(option) }, label = { Text(label(option)) })
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
