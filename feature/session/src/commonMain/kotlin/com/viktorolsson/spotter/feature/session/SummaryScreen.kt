package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.Failure
import com.viktorolsson.spotter.core.data.launchReporting
import com.viktorolsson.spotter.core.data.repository.GamificationRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.engine.Gamification
import com.viktorolsson.spotter.core.engine.GamificationRules
import com.viktorolsson.spotter.core.model.Badge
import com.viktorolsson.spotter.core.model.BadgeProgress
import com.viktorolsson.spotter.core.model.Level
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.PlayerProgress
import com.viktorolsson.spotter.core.model.SessionXp
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.component.BadgeTile
import com.viktorolsson.spotter.core.ui.component.Confetti
import com.viktorolsson.spotter.core.ui.component.ContentCard
import com.viktorolsson.spotter.core.ui.component.ErrorState
import com.viktorolsson.spotter.core.ui.component.FeelPicker
import com.viktorolsson.spotter.core.ui.component.LevelProgress
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.RecordsCard
import com.viktorolsson.spotter.core.ui.component.ShareButton
import com.viktorolsson.spotter.core.ui.component.StatTile
import com.viktorolsson.spotter.core.ui.component.captureForShare
import com.viktorolsson.spotter.core.ui.component.levelHeading
import com.viktorolsson.spotter.core.ui.component.rememberImageSharer
import com.viktorolsson.spotter.core.ui.component.rememberShareCapture
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatCount
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.formatSetCount
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.feature.session.resources.Res
import com.viktorolsson.spotter.feature.session.resources.summary_done
import com.viktorolsson.spotter.feature.session.resources.summary_duration
import com.viktorolsson.spotter.feature.session.resources.summary_effort
import com.viktorolsson.spotter.feature.session.resources.summary_effort_unset
import com.viktorolsson.spotter.feature.session.resources.summary_effort_value
import com.viktorolsson.spotter.feature.session.resources.summary_exercise_line
import com.viktorolsson.spotter.feature.session.resources.summary_feel_title
import com.viktorolsson.spotter.feature.session.resources.summary_level_up
import com.viktorolsson.spotter.feature.session.resources.summary_new_badges
import com.viktorolsson.spotter.feature.session.resources.summary_new_prs
import com.viktorolsson.spotter.feature.session.resources.summary_sets
import com.viktorolsson.spotter.feature.session.resources.summary_share_badges
import com.viktorolsson.spotter.feature.session.resources.summary_share_level
import com.viktorolsson.spotter.feature.session.resources.summary_title
import com.viktorolsson.spotter.feature.session.resources.summary_volume
import com.viktorolsson.spotter.feature.session.resources.summary_xp_check_in
import com.viktorolsson.spotter.feature.session.resources.summary_xp_none
import com.viktorolsson.spotter.feature.session.resources.summary_xp_quests
import com.viktorolsson.spotter.feature.session.resources.summary_xp_rate_hint
import com.viktorolsson.spotter.feature.session.resources.summary_xp_records
import com.viktorolsson.spotter.feature.session.resources.summary_xp_session
import com.viktorolsson.spotter.feature.session.resources.summary_xp_sets
import com.viktorolsson.spotter.feature.session.resources.summary_xp_title
import com.viktorolsson.spotter.feature.session.resources.summary_xp_week_goal
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** What this workout did for the level: its XP, the level now and the badges it unlocked. */
data class SummaryXp(
    val earned: SessionXp,
    val level: Level,
    /** Where the bar stood before this workout (0–1); 0 after a level-up. */
    val from: Float,
    val levelUp: Boolean,
    val badges: List<Badge>,
) {
    companion object {
        fun of(progress: PlayerProgress, sessionId: Long): SummaryXp? {
            val earned = progress.xpFor(sessionId) ?: return null
            val before = Gamification.level(progress.totalXp - earned.total)
            val levelUp = before.number < progress.level.number
            return SummaryXp(
                earned = earned,
                level = progress.level,
                from = if (levelUp || before.xpForNext == 0) 0f else before.xpIntoLevel.toFloat() / before.xpForNext,
                levelUp = levelUp,
                badges = progress.unlockedBy(sessionId),
            )
        }
    }
}

data class SummaryUiState(
    val loading: Boolean = true,
    /** Null once loaded means the workout is gone. */
    val summary: WorkoutSummary? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val records: List<PersonalRecord> = emptyList(),
    /** How the session went, 1–5, once rated. */
    val feel: Int? = null,
    /** Session effort, 1–10, once rated. */
    val effort: Int? = null,
    /** Null when levels are switched off. */
    val xp: SummaryXp? = null,
)

class SummaryViewModel(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    historyRepository: HistoryRepository,
    preferencesRepository: UserPreferencesRepository,
    gamificationRepository: GamificationRepository,
    private val errors: AppErrors,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<SummaryRoute>().sessionId

    val uiState: StateFlow<SummaryUiState> = combine(
        workoutRepository.observeSession(sessionId),
        historyRepository.observeSessionRecords(sessionId),
        preferencesRepository.preferences,
        gamificationRepository.observeProgress(),
    ) { session, records, prefs, progress ->
        SummaryUiState(
            false, session?.let { WorkoutSummary.of(it) }, prefs.weightUnit, records, session?.feel, session?.effort,
            xp = if (prefs.showLevels) SummaryXp.of(progress, sessionId) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    fun rateFeel(feel: Int) = viewModelScope.launchReporting(errors, Failure.SAVE) {
        workoutRepository.setSessionFeel(sessionId, feel)
    }

    fun rateEffort(effort: Int) = viewModelScope.launchReporting(errors, Failure.SAVE) {
        workoutRepository.setSessionEffort(sessionId, effort)
    }

    /** Runs a share; a failure is shown, not swallowed. */
    fun share(block: suspend () -> Unit) = viewModelScope.launchReporting(errors, Failure.ACTION) { block() }
}

@Composable
internal fun SummaryRoute(onDone: () -> Unit, viewModel: SummaryViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    when {
        uiState.loading -> Scaffold { padding -> LoadingState(Modifier.padding(padding).fillMaxSize()) }
        summary == null -> Scaffold(
            bottomBar = { PrimaryButton(stringResource(Res.string.summary_done), onDone, Modifier.navigationBarsPadding().padding(Dimens.ScreenPadding)) },
        ) { padding -> ErrorState(Modifier.padding(padding).fillMaxSize()) }
        else -> Box {
            SummaryScreen(
                summary, uiState.unit, uiState.records, onDone,
                feel = uiState.feel, effort = uiState.effort, onFeel = viewModel::rateFeel, onEffort = viewModel::rateEffort,
                xp = uiState.xp, onShare = viewModel::share,
            )
            val levelUp = uiState.xp?.levelUp == true
            if (uiState.records.isNotEmpty() || levelUp) Confetti(key = uiState.records.size to levelUp)
        }
    }
}

@Composable
internal fun SummaryScreen(
    summary: WorkoutSummary,
    unit: WeightUnit,
    records: List<PersonalRecord>,
    onDone: () -> Unit,
    feel: Int? = null,
    effort: Int? = null,
    onFeel: (Int) -> Unit = {},
    onEffort: (Int) -> Unit = {},
    xp: SummaryXp? = null,
    onShare: (suspend () -> Unit) -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            PrimaryButton(
                text = stringResource(Res.string.summary_done),
                onClick = onDone,
                modifier = Modifier.navigationBarsPadding().padding(Dimens.ScreenPadding),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
        ) {
            item(key = "title") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.IconLarge),
                    )
                    Text(
                        stringResource(Res.string.summary_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(key = "stats") {
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(stringResource(Res.string.summary_duration), formatClock(summary.duration), Modifier.weight(1f))
                    StatTile(stringResource(Res.string.summary_volume), formatTotal(summary.volumeKg, unit), Modifier.weight(1f))
                    StatTile(stringResource(Res.string.summary_sets), summary.completedSets.toString(), Modifier.weight(1f))
                }
            }
            if (xp != null) item(key = "xp") { ShareableXpCard(xp, onShare) }
            item(key = "rating") { RatingCard(feel, effort, onFeel, onEffort) }
            if (records.isNotEmpty()) {
                item(key = "records") {
                    RecordsCard(pluralStringResource(Res.plurals.summary_new_prs, records.size, records.size), records, unit)
                }
            }
            itemsIndexed(summary.exercises, key = { i, ex -> "$i-${ex.exerciseName}" }) { _, ex ->
                val best = ex.bestSet?.let { set -> formatSet(set.weightKg, set.reps, unit, ex.timed) }
                ListItem(
                    headlineContent = { Text(ex.exerciseName) },
                    supportingContent = {
                        Text(
                            if (best != null) stringResource(Res.string.summary_exercise_line, formatSetCount(ex.completedSets), best)
                            else formatSetCount(ex.completedSets),
                        )
                    },
                )
            }
        }
    }
}

/** The XP card, with Share under it after a level-up or a new badge: the card is the image. */
@Composable
private fun ShareableXpCard(xp: SummaryXp, onShare: (suspend () -> Unit) -> Unit) {
    if (!xp.levelUp && xp.badges.isEmpty()) return XpCard(xp)
    val capture = rememberShareCapture()
    val sharer = rememberImageSharer()
    val title = levelHeading(xp.level)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        XpCard(xp, Modifier.captureForShare(capture))
        ShareButton(
            onClick = {
                onShare {
                    val text = if (xp.levelUp) getString(Res.string.summary_share_level, title) else getString(Res.string.summary_share_badges)
                    sharer.share(capture.image(), text)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "+275 XP", where it came from, the level bar filling up and any badges this workout unlocked. */
@Composable
private fun XpCard(xp: SummaryXp, modifier: Modifier = Modifier) {
    val earned = xp.earned
    ContentCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(Res.string.summary_xp_title, formatCount(earned.total)),
                    style = SpotterTheme.type.numberMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                val parts = listOfNotNull(
                    earned.session.takeIf { it > 0 }?.let { stringResource(Res.string.summary_xp_session, it) },
                    earned.sets.takeIf { it > 0 }?.let { stringResource(Res.string.summary_xp_sets, it) },
                    earned.records.takeIf { it > 0 }?.let { stringResource(Res.string.summary_xp_records, it) },
                    earned.checkIn.takeIf { it > 0 }?.let { stringResource(Res.string.summary_xp_check_in, it) },
                    earned.weeklyGoal.takeIf { it > 0 }?.let { stringResource(Res.string.summary_xp_week_goal, it) },
                    earned.quests.takeIf { it > 0 }?.let { stringResource(Res.string.summary_xp_quests, it) },
                )
                Text(
                    if (parts.isEmpty()) stringResource(Res.string.summary_xp_none) else parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (earned.session > 0 && earned.checkIn == 0) {
                    Text(
                        stringResource(Res.string.summary_xp_rate_hint, GamificationRules.CHECK_IN_XP),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (xp.levelUp) {
                Text(
                    stringResource(Res.string.summary_level_up),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            LevelProgress(xp.level, from = xp.from)
            if (xp.badges.isNotEmpty()) {
                Text(
                    pluralStringResource(Res.plurals.summary_new_badges, xp.badges.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                xp.badges.forEach { badge ->
                    BadgeTile(BadgeProgress(badge, badge.target, unlockedBy = earned.sessionId, unlockedOn = null), Modifier.fillMaxWidth(), showStatus = false)
                }
            }
        }
    }
}

/** "How did it go?" (1–5) and an optional effort (1–10), both saved as soon as they change. */
@Composable
private fun RatingCard(feel: Int?, effort: Int?, onFeel: (Int) -> Unit, onEffort: (Int) -> Unit) {
    ContentCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                stringResource(Res.string.summary_feel_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            FeelPicker(selected = feel, onSelect = onFeel)
            // The slider moves locally and saves when let go, so dragging writes once.
            var dragging by remember(effort) { mutableStateOf(effort?.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.summary_effort), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    dragging?.let { stringResource(Res.string.summary_effort_value, it.roundToInt()) } ?: stringResource(Res.string.summary_effort_unset),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = dragging ?: 5f,
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { onEffort(it.roundToInt()) } },
                valueRange = 1f..10f,
                steps = 8,
            )
        }
    }
}
