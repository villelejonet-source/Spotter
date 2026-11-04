package com.viktorolsson.spotter.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.Failure
import com.viktorolsson.spotter.core.data.launchReporting
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.GamificationRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.model.BadgeProgress
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.YearInReview
import com.viktorolsson.spotter.core.ui.component.BadgeTile
import com.viktorolsson.spotter.core.ui.component.CardTitle
import com.viktorolsson.spotter.core.ui.component.ContentCard
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.ShareButton
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.component.StatTile
import com.viktorolsson.spotter.core.ui.component.captureForShare
import com.viktorolsson.spotter.core.ui.component.rememberImageSharer
import com.viktorolsson.spotter.core.ui.component.rememberShareCapture
import com.viktorolsson.spotter.core.ui.formatCount
import com.viktorolsson.spotter.core.ui.formatMonthName
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.progress.resources.Res
import com.viktorolsson.spotter.feature.progress.resources.year_badges
import com.viktorolsson.spotter.feature.progress.resources.year_empty
import com.viktorolsson.spotter.feature.progress.resources.year_empty_title
import com.viktorolsson.spotter.feature.progress.resources.year_favourite
import com.viktorolsson.spotter.feature.progress.resources.year_heading
import com.viktorolsson.spotter.feature.progress.resources.year_hours
import com.viktorolsson.spotter.feature.progress.resources.year_levels
import com.viktorolsson.spotter.feature.progress.resources.year_no_badges
import com.viktorolsson.spotter.feature.progress.resources.year_records
import com.viktorolsson.spotter.feature.progress.resources.year_share_text
import com.viktorolsson.spotter.feature.progress.resources.year_strongest_month
import com.viktorolsson.spotter.feature.progress.resources.year_time
import com.viktorolsson.spotter.feature.progress.resources.year_title
import com.viktorolsson.spotter.feature.progress.resources.year_volume
import com.viktorolsson.spotter.feature.progress.resources.year_workouts
import com.viktorolsson.spotter.feature.progress.resources.year_xp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class YearInReviewUiState(
    val loading: Boolean = true,
    val year: Int,
    val review: YearInReview? = null,
    /** The year's badges, as unlocked. */
    val badges: List<BadgeProgress> = emptyList(),
    val favouriteName: String? = null,
    val unit: WeightUnit = WeightUnit.KG,
    /** Levels were switched off (on another phone, say) while this screen was open. */
    val hidden: Boolean = false,
)

class YearInReviewViewModel(
    savedStateHandle: SavedStateHandle,
    gamificationRepository: GamificationRepository,
    exerciseRepository: ExerciseRepository,
    preferencesRepository: UserPreferencesRepository,
    private val errors: AppErrors,
) : ViewModel() {
    private val year = savedStateHandle.toRoute<YearInReviewRoute>().year

    val uiState: StateFlow<YearInReviewUiState> = combine(
        gamificationRepository.observeLevels(year),
        preferencesRepository.preferences,
    ) { levels, prefs ->
        val review = levels.yearInReview
        YearInReviewUiState(
            loading = false,
            year = year,
            review = review,
            badges = levels.progress.badges.filter { it.badge in review?.badges.orEmpty() },
            favouriteName = review?.favouriteExercise?.let { exerciseRepository.getById(it)?.name },
            unit = prefs.weightUnit,
            hidden = !prefs.showLevels,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearInReviewUiState(year = year))

    /** Runs a share; a failure is shown, not swallowed. */
    fun share(block: suspend () -> Unit) = viewModelScope.launchReporting(errors, Failure.ACTION) { block() }
}

@Composable
internal fun YearInReviewRoute(onBack: () -> Unit, viewModel: YearInReviewViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.hidden) { if (state.hidden) onBack() }
    YearInReviewScreen(state, onBack, viewModel::share)
}

/** A year of training as a card to share, then the badges unlocked that year. */
@Composable
internal fun YearInReviewScreen(state: YearInReviewUiState, onBack: () -> Unit, onShare: (suspend () -> Unit) -> Unit = {}) {
    Scaffold(topBar = { SpotterTopBar(title = stringResource(Res.string.year_title, state.year), onBack = onBack) }) { padding ->
        val review = state.review
        when {
            state.loading -> LoadingState(Modifier.padding(padding).fillMaxSize())
            review == null -> EmptyState(
                Icons.Rounded.EmojiEvents,
                stringResource(Res.string.year_empty_title, state.year),
                stringResource(Res.string.year_empty),
                Modifier.padding(padding).fillMaxSize(),
            )
            else -> Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
            ) {
                val capture = rememberShareCapture()
                val sharer = rememberImageSharer()
                YearCard(review, state, Modifier.captureForShare(capture))
                ShareButton(
                    onClick = {
                        onShare {
                            val text = getString(Res.string.year_share_text, review.year, review.workouts, review.levelStart, review.levelEnd)
                            sharer.share(capture.image(), text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                CardTitle(stringResource(Res.string.year_badges))
                if (state.badges.isEmpty()) {
                    Text(stringResource(Res.string.year_no_badges), style = MaterialTheme.typography.bodyMedium)
                }
                state.badges.forEach { BadgeTile(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun YearCard(review: YearInReview, state: YearInReviewUiState, modifier: Modifier = Modifier) {
    ContentCard(modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.year_heading, review.year), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(Res.string.year_levels, review.levelStart, review.levelEnd),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Dimens.ListGap)) {
            StatTile(stringResource(Res.string.year_workouts), formatCount(review.workouts), Modifier.weight(1f))
            StatTile(stringResource(Res.string.year_time), stringResource(Res.string.year_hours, (review.minutes + 30) / 60), Modifier.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Dimens.ListGap)) {
            StatTile(stringResource(Res.string.year_volume), formatTotal(review.volumeKg, state.unit), Modifier.weight(1f))
            StatTile(stringResource(Res.string.year_records), formatCount(review.records), Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            review.strongestMonth?.let { month ->
                Highlight(stringResource(Res.string.year_strongest_month), formatMonthName(YearMonth(review.year, month)))
            }
            state.favouriteName?.let { Highlight(stringResource(Res.string.year_favourite), it) }
            Highlight(stringResource(Res.string.year_xp), formatCount(review.xp))
        }
    }
}

@Composable
private fun Highlight(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
