package com.viktorolsson.spotter.feature.onboarding

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.component.BackButton
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.SegmentedChoice
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.onboarding.resources.Res
import com.viktorolsson.spotter.feature.onboarding.resources.onb_age
import com.viktorolsson.spotter.feature.onboarding.resources.onb_body_subtitle
import com.viktorolsson.spotter.feature.onboarding.resources.onb_body_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_build
import com.viktorolsson.spotter.feature.onboarding.resources.onb_days_hint
import com.viktorolsson.spotter.feature.onboarding.resources.onb_days_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_bodyweight
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_bodyweight_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_dumbbells
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_dumbbells_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_full
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_full_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_home
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_home_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_items
import com.viktorolsson.spotter.feature.onboarding.resources.onb_equipment_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_exp_advanced
import com.viktorolsson.spotter.feature.onboarding.resources.onb_exp_advanced_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_exp_intermediate
import com.viktorolsson.spotter.feature.onboarding.resources.onb_exp_intermediate_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_exp_new
import com.viktorolsson.spotter.feature.onboarding.resources.onb_exp_new_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_exp_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_focus_subtitle
import com.viktorolsson.spotter.feature.onboarding.resources.onb_focus_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_fat_loss
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_fat_loss_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_general
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_general_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_hypertrophy
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_hypertrophy_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_strength
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_strength_sub
import com.viktorolsson.spotter.feature.onboarding.resources.onb_goal_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_height_cm
import com.viktorolsson.spotter.feature.onboarding.resources.onb_height_ft
import com.viktorolsson.spotter.feature.onboarding.resources.onb_height_in
import com.viktorolsson.spotter.feature.onboarding.resources.onb_length_long
import com.viktorolsson.spotter.feature.onboarding.resources.onb_length_option
import com.viktorolsson.spotter.feature.onboarding.resources.onb_length_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_lift_bench
import com.viktorolsson.spotter.feature.onboarding.resources.onb_lift_deadlift
import com.viktorolsson.spotter.feature.onboarding.resources.onb_lift_reps
import com.viktorolsson.spotter.feature.onboarding.resources.onb_lift_squat
import com.viktorolsson.spotter.feature.onboarding.resources.onb_lift_weight
import com.viktorolsson.spotter.feature.onboarding.resources.onb_lifts_subtitle
import com.viktorolsson.spotter.feature.onboarding.resources.onb_lifts_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_limit_knee
import com.viktorolsson.spotter.feature.onboarding.resources.onb_limit_lower_back
import com.viktorolsson.spotter.feature.onboarding.resources.onb_limit_shoulder
import com.viktorolsson.spotter.feature.onboarding.resources.onb_limit_wrist
import com.viktorolsson.spotter.feature.onboarding.resources.onb_limits_note
import com.viktorolsson.spotter.feature.onboarding.resources.onb_limits_subtitle
import com.viktorolsson.spotter.feature.onboarding.resources.onb_limits_title
import com.viktorolsson.spotter.feature.onboarding.resources.onb_next
import com.viktorolsson.spotter.feature.onboarding.resources.onb_sex
import com.viktorolsson.spotter.feature.onboarding.resources.onb_sex_female
import com.viktorolsson.spotter.feature.onboarding.resources.onb_sex_male
import com.viktorolsson.spotter.feature.onboarding.resources.onb_skip
import com.viktorolsson.spotter.feature.onboarding.resources.onb_start
import com.viktorolsson.spotter.feature.onboarding.resources.onb_units_imperial
import com.viktorolsson.spotter.feature.onboarding.resources.onb_units_metric
import com.viktorolsson.spotter.feature.onboarding.resources.onb_weight

@Composable
internal fun OnboardingRoute(
    onDone: () -> Unit,
    onSignIn: (newAccount: Boolean) -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.restoredFromBackup.collect { onDone() } }
    when {
        // Back on the welcome screen leaves the app as usual.
        state.welcome -> {
            WelcomeScreen(
                onCreateAccount = { onSignIn(true) },
                onSignIn = { onSignIn(false) },
                onContinueWithout = viewModel::continueWithoutAccount,
            )
            return
        }
        state.checkingBackup -> {
            CheckingBackup()
            return
        }
    }
    @OptIn(ExperimentalComposeUiApi::class)
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
                SpotterTopBar(
                    title = {},
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        if (!state.rebuild && state.step == Step.BODY) {
                            TextButton(onClick = onSkip) { Text(stringResource(Res.string.onb_skip)) }
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { (state.step.ordinal + 1f) / Step.entries.size },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.FocusedScreenPadding),
                )
            }
        },
        bottomBar = {
            val (label, action, enabled) = when (state.step) {
                Step.LIFTS -> Triple(Res.string.onb_build, onNext, true)
                Step.SUMMARY -> Triple(Res.string.onb_start, onFinish, state.generated != null && !state.saving)
                else -> Triple(Res.string.onb_next, onNext, answers.isValid(state.step))
            }
            PrimaryButton(
                text = stringResource(label),
                onClick = action,
                enabled = enabled,
                modifier = Modifier.navigationBarsPadding().imePadding().padding(Dimens.ScreenPadding),
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "step",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { step ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.FocusedScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when (step) {
                    Step.BODY -> BodyStep(answers, onUpdate, onImperial)
                    Step.GOAL -> {
                        Title(Res.string.onb_goal_title)
                        listOf(
                            Triple(Goal.STRENGTH, Res.string.onb_goal_strength, Res.string.onb_goal_strength_sub),
                            Triple(Goal.HYPERTROPHY, Res.string.onb_goal_hypertrophy, Res.string.onb_goal_hypertrophy_sub),
                            Triple(Goal.GENERAL_FITNESS, Res.string.onb_goal_general, Res.string.onb_goal_general_sub),
                            Triple(Goal.FAT_LOSS, Res.string.onb_goal_fat_loss, Res.string.onb_goal_fat_loss_sub),
                        ).forEach { (goal, title, sub) ->
                            OptionCard(stringResource(title), answers.goal == goal, { onChoose { it.copy(goal = goal) } }, stringResource(sub))
                        }
                    }
                    Step.EXPERIENCE -> {
                        Title(Res.string.onb_exp_title)
                        listOf(
                            Triple(ExperienceLevel.NEW, Res.string.onb_exp_new, Res.string.onb_exp_new_sub),
                            Triple(ExperienceLevel.INTERMEDIATE, Res.string.onb_exp_intermediate, Res.string.onb_exp_intermediate_sub),
                            Triple(ExperienceLevel.ADVANCED, Res.string.onb_exp_advanced, Res.string.onb_exp_advanced_sub),
                        ).forEach { (level, title, sub) ->
                            OptionCard(stringResource(title), answers.experience == level, { onChoose { it.copy(experience = level) } }, stringResource(sub))
                        }
                    }
                    Step.DAYS -> {
                        Title(Res.string.onb_days_title)
                        (2..6).forEach { days ->
                            OptionCard(pluralStringResource(Res.plurals.onb_days_hint, days, days), answers.days == days, { onChoose { it.copy(days = days) } })
                        }
                    }
                    Step.LENGTH -> {
                        Title(Res.string.onb_length_title)
                        listOf(30, 45, 60, 75).forEach { minutes ->
                            val label = if (minutes == 75) stringResource(Res.string.onb_length_long) else stringResource(Res.string.onb_length_option, minutes)
                            OptionCard(label, answers.minutes == minutes, { onChoose { it.copy(minutes = minutes) } })
                        }
                    }
                    Step.EQUIPMENT -> EquipmentStep(answers, onUpdate)
                    Step.FOCUS -> {
                        Title(Res.string.onb_focus_title, Res.string.onb_focus_subtitle)
                        ChipGroup(BodyArea.entries, answers.focus, { stringResource(it.labelRes) }) { area ->
                            onUpdate { it.copy(focus = it.focus.toggle(area), focusTouched = true) }
                        }
                    }
                    Step.LIMITATIONS -> {
                        Title(Res.string.onb_limits_title, Res.string.onb_limits_subtitle)
                        listOf(
                            Limitation.SHOULDER to Res.string.onb_limit_shoulder,
                            Limitation.KNEE to Res.string.onb_limit_knee,
                            Limitation.LOWER_BACK to Res.string.onb_limit_lower_back,
                            Limitation.WRIST to Res.string.onb_limit_wrist,
                        ).forEach { (limitation, label) ->
                            OptionCard(stringResource(label), limitation in answers.limitations, {
                                onUpdate { it.copy(limitations = it.limitations.toggle(limitation)) }
                            })
                        }
                        Text(
                            stringResource(Res.string.onb_limits_note),
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
internal fun Title(title: StringResource, subtitle: StringResource? = null) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (subtitle != null) {
        Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BodyStep(answers: Answers, onUpdate: ((Answers) -> Answers) -> Unit, onImperial: (Boolean) -> Unit) {
    Title(Res.string.onb_body_title, Res.string.onb_body_subtitle)
    Text(stringResource(Res.string.onb_sex), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
    SegmentedChoice(
        options = listOf(Sex.FEMALE to stringResource(Res.string.onb_sex_female), Sex.MALE to stringResource(Res.string.onb_sex_male)),
        selected = answers.sex,
        onSelect = { sex -> onUpdate { it.copy(sex = sex) } },
    )
    SegmentedChoice(
        options = listOf(false to stringResource(Res.string.onb_units_metric), true to stringResource(Res.string.onb_units_imperial)),
        selected = answers.imperial,
        onSelect = onImperial,
    )
    NumberInput(answers.age, { v -> onUpdate { it.copy(age = v) } }, stringResource(Res.string.onb_age), Modifier.fillMaxWidth())
    if (answers.imperial) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            NumberInput(answers.heightFt, { v -> onUpdate { it.copy(heightFt = v) } }, stringResource(Res.string.onb_height_ft), Modifier.weight(1f))
            NumberInput(answers.heightIn, { v -> onUpdate { it.copy(heightIn = v) } }, stringResource(Res.string.onb_height_in), Modifier.weight(1f))
        }
    } else {
        NumberInput(answers.heightCm, { v -> onUpdate { it.copy(heightCm = v) } }, stringResource(Res.string.onb_height_cm), Modifier.fillMaxWidth())
    }
    NumberInput(
        answers.weight,
        { v -> onUpdate { it.copy(weight = v) } },
        stringResource(Res.string.onb_weight, answers.unit.label),
        Modifier.fillMaxWidth(),
        decimal = true,
    )
}

@Composable
private fun EquipmentStep(answers: Answers, onUpdate: ((Answers) -> Answers) -> Unit) {
    Title(Res.string.onb_equipment_title)
    listOf(
        Triple(EquipmentPreset.FULL_GYM, Res.string.onb_equipment_full, Res.string.onb_equipment_full_sub),
        Triple(EquipmentPreset.HOME_GYM, Res.string.onb_equipment_home, Res.string.onb_equipment_home_sub),
        Triple(EquipmentPreset.DUMBBELLS, Res.string.onb_equipment_dumbbells, Res.string.onb_equipment_dumbbells_sub),
        Triple(EquipmentPreset.BODYWEIGHT, Res.string.onb_equipment_bodyweight, Res.string.onb_equipment_bodyweight_sub),
    ).forEach { (preset, title, sub) ->
        OptionCard(stringResource(title), answers.equipment == preset.equipment, {
            onUpdate { it.copy(equipment = preset.equipment) }
        }, stringResource(sub))
    }
    Text(stringResource(Res.string.onb_equipment_items), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
    ChipGroup(Equipment.entries, answers.equipment, { stringResource(it.labelRes) }) { item ->
        onUpdate { it.copy(equipment = it.equipment.toggle(item)) }
    }
}

@Composable
private fun LiftsStep(answers: Answers, onUpdate: ((Answers) -> Answers) -> Unit) {
    Title(Res.string.onb_lifts_title, Res.string.onb_lifts_subtitle)
    listOf(
        Lift.BENCH to Res.string.onb_lift_bench,
        Lift.SQUAT to Res.string.onb_lift_squat,
        Lift.DEADLIFT to Res.string.onb_lift_deadlift,
    ).forEach { (lift, label) ->
        val input = answers.lifts[lift] ?: LiftInput()
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            NumberInput(
                input.weight,
                { v -> onUpdate { it.copy(lifts = it.lifts + (lift to input.copy(weight = v))) } },
                stringResource(Res.string.onb_lift_weight, answers.unit.label),
                Modifier.weight(1f),
                decimal = true,
            )
            NumberInput(
                input.reps,
                { v -> onUpdate { it.copy(lifts = it.lifts + (lift to input.copy(reps = v))) } },
                stringResource(Res.string.onb_lift_reps),
                Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(options: List<T>, selected: Set<T>, label: @Composable (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { option ->
            FilterChip(selected = option in selected, onClick = { onToggle(option) }, label = { Text(label(option)) })
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
