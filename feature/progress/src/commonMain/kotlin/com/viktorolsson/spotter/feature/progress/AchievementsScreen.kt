package com.viktorolsson.spotter.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.GamificationRepository
import com.viktorolsson.spotter.core.engine.GamificationRules
import com.viktorolsson.spotter.core.model.PlayerProgress
import com.viktorolsson.spotter.core.ui.component.BadgeTile
import com.viktorolsson.spotter.core.ui.component.CardTitle
import com.viktorolsson.spotter.core.ui.component.ContentCard
import com.viktorolsson.spotter.core.ui.component.LevelProgress
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.SectionHeader
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.formatCount
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.feature.progress.resources.Res
import com.viktorolsson.spotter.feature.progress.resources.achievements_badges
import com.viktorolsson.spotter.feature.progress.resources.achievements_how_check_in
import com.viktorolsson.spotter.feature.progress.resources.achievements_how_limits
import com.viktorolsson.spotter.feature.progress.resources.achievements_how_records
import com.viktorolsson.spotter.feature.progress.resources.achievements_how_session
import com.viktorolsson.spotter.feature.progress.resources.achievements_how_sets
import com.viktorolsson.spotter.feature.progress.resources.achievements_how_title
import com.viktorolsson.spotter.feature.progress.resources.achievements_how_week_goal
import com.viktorolsson.spotter.feature.progress.resources.achievements_title
import com.viktorolsson.spotter.feature.progress.resources.achievements_xp_total
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class AchievementsUiState(val loading: Boolean = true, val progress: PlayerProgress? = null)

class AchievementsViewModel(gamificationRepository: GamificationRepository) : ViewModel() {
    val uiState: StateFlow<AchievementsUiState> = gamificationRepository.observeProgress()
        .map { AchievementsUiState(loading = false, progress = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())
}

@Composable
internal fun AchievementsRoute(onBack: () -> Unit, viewModel: AchievementsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AchievementsScreen(state, onBack)
}

/** The level, how XP is earned, and every badge: unlocked with its date, locked with how far along. */
@Composable
internal fun AchievementsScreen(state: AchievementsUiState, onBack: () -> Unit) {
    Scaffold(topBar = { SpotterTopBar(title = stringResource(Res.string.achievements_title), onBack = onBack) }) { padding ->
        val progress = state.progress
        if (state.loading || progress == null) {
            LoadingState(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(BADGE_MIN_WIDTH),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.ListGap),
            verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
        ) {
            item(key = "level", span = { GridItemSpan(maxLineSpan) }) {
                ContentCard(Modifier.fillMaxWidth()) {
                    LevelProgress(progress.level)
                    Text(
                        stringResource(Res.string.achievements_xp_total, formatCount(progress.totalXp)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "how", span = { GridItemSpan(maxLineSpan) }) { HowCard() }
            item(key = "badges", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(Res.string.achievements_badges, progress.badges.count { it.unlocked }, progress.badges.size))
            }
            items(progress.badges, key = { it.badge.name }) { badge -> BadgeTile(badge, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun HowCard() {
    ContentCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(Res.string.achievements_how_title))
        listOf(
            stringResource(Res.string.achievements_how_session, GamificationRules.SESSION_XP),
            stringResource(Res.string.achievements_how_sets, GamificationRules.SET_XP, GamificationRules.MAX_XP_SETS),
            stringResource(Res.string.achievements_how_records, GamificationRules.RECORD_XP),
            stringResource(Res.string.achievements_how_check_in, GamificationRules.CHECK_IN_XP),
            stringResource(Res.string.achievements_how_week_goal, GamificationRules.WEEKLY_GOAL_XP),
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(
            stringResource(Res.string.achievements_how_limits, GamificationRules.MIN_MINUTES, GamificationRules.XP_SESSIONS_PER_DAY),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val BADGE_MIN_WIDTH = 160.dp
