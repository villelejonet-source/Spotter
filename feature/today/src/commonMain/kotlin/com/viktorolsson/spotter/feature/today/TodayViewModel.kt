package com.viktorolsson.spotter.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.Failure
import com.viktorolsson.spotter.core.data.launchReporting
import com.viktorolsson.spotter.core.data.repository.GamificationRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.ScheduleRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.engine.LiftOption
import com.viktorolsson.spotter.core.engine.PlanReview
import com.viktorolsson.spotter.core.engine.StalledLift
import com.viktorolsson.spotter.core.model.Level
import com.viktorolsson.spotter.core.model.WeekQuests
import com.viktorolsson.spotter.core.model.YearInReview
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.ScheduledWorkout
import com.viktorolsson.spotter.core.model.TrainedDay
import com.viktorolsson.spotter.core.model.TrainingWeek
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.model.percentChange
import com.viktorolsson.spotter.core.model.weekStreak
import com.viktorolsson.spotter.core.model.weeklyTotals
import com.viktorolsson.spotter.core.model.windowStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class TodayUiState(
    val loading: Boolean = true,
    /** Shown under the title. */
    val today: LocalDate? = null,
    val hasPlan: Boolean = false,
    val nextDay: PlanDay? = null,
    val activeSessionId: Long? = null,
    /** Last day of a deload week in progress. */
    val deloadUntil: LocalDate? = null,
    /** Planned for today and not done yet: it takes the place of [nextDay]. */
    val scheduledToday: ScheduledWorkout? = null,
    /** [scheduledToday]'s plan day, for the exercise preview; null for a free workout. */
    val scheduledDay: PlanDay? = null,
    /** Everything planned for today has been done. */
    val scheduledTodayDone: Boolean = false,
    /** Planned for the rest of the week (not today). */
    val upcoming: List<ScheduledWorkout> = emptyList(),
    /** Null until the first workout is finished. */
    val dashboard: Dashboard? = null,
)

/** The level card on Today. */
data class TodayLevel(
    val level: Level,
    val badgesUnlocked: Int,
    val badgesTotal: Int,
    /** First time since levels arrived: say what they are. */
    val intro: Boolean,
    /** This week's quests; null before quests began. */
    val quests: WeekQuests? = null,
    /** From December 1 to January 15, the year to look back on. */
    val yearInReview: YearInReview? = null,
)

/** The training numbers above the fold: this week, volume, streak and the heatmap. */
data class Dashboard(
    val today: LocalDate,
    /** The last [HEATMAP_WEEKS] weeks, oldest first; the stats use the last [STAT_WEEKS]. */
    val weeks: List<TrainingWeek>,
    /** Workouts per day over [weeks], for the heatmap. */
    val workoutsByDay: Map<LocalDate, Int>,
    /** The profile's days per week, if it has one. */
    val weeklyGoal: Int?,
    /** Weeks in a row, counted inside [weeks]: at [HEATMAP_WEEKS] it may be longer. */
    val streak: Int,
    val totalWorkouts: Int,
    val unit: WeightUnit,
) {
    val thisWeek: TrainingWeek get() = weeks.last()
    val statWeeks: List<TrainingWeek> get() = weeks.takeLast(STAT_WEEKS)
    val fourWeekVolumeKg: Double get() = weeks.takeLast(4).sumOf { it.volumeKg }

    /** The last four weeks' volume against the four before, in percent. */
    val volumeChange: Int? get() = percentChange(fourWeekVolumeKg, weeks.dropLast(4).takeLast(4).sumOf { it.volumeKg })

    companion object {
        const val HEATMAP_WEEKS = 18
        const val STAT_WEEKS = 12
    }
}

private data class Schedule(
    val date: LocalDate,
    val today: ScheduledWorkout?,
    val day: PlanDay?,
    val todayDone: Boolean,
    val upcoming: List<ScheduledWorkout>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    planRepository: PlanRepository,
    private val workoutRepository: WorkoutRepository,
    private val scheduleRepository: ScheduleRepository,
    historyRepository: HistoryRepository,
    profileRepository: UserProfileRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val recommendationRepository: RecommendationRepository,
    gamificationRepository: GamificationRepository,
    private val errors: AppErrors,
    private val clock: Clock,
) : ViewModel() {
    /** Null when levels are switched off in Settings. */
    val level: StateFlow<TodayLevel?> = combine(gamificationRepository.observeLevels(), preferencesRepository.preferences) { levels, prefs ->
        if (!prefs.showLevels) return@combine null
        val progress = levels.progress
        TodayLevel(
            progress.level, progress.badges.count { it.unlocked }, progress.badges.size, intro = !prefs.levelsIntroSeen,
            quests = progress.thisWeek, yearInReview = levels.yearInReview,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun levelsIntroSeen() = viewModelScope.launchReporting(errors) { preferencesRepository.setLevelsIntroSeen() }

    /** The plan review, when one is due. */
    val review: StateFlow<PlanReview?> = recommendationRepository.observePlanReview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun applyReview(picks: List<Pair<StalledLift, LiftOption>>) = viewModelScope.launchReporting(errors, Failure.SAVE) {
        recommendationRepository.applyPlanReview(picks)
    }

    fun keepPlan() = viewModelScope.launchReporting(errors, Failure.SAVE) { recommendationRepository.keepPlan() }

    fun snoozeReview() = viewModelScope.launchReporting(errors, Failure.SAVE) { recommendationRepository.snoozePlanReview() }

    /**
     * The phone's date, checked every minute. Today is the start screen, so this view model
     * lives as long as the app; whatever depends on the date is rebuilt when it changes.
     */
    private val date: Flow<LocalDate> = flow {
        while (true) {
            emit(clock.todayIn(TimeZone.currentSystemDefault()))
            delay(1.minutes)
        }
    }.distinctUntilChanged()

    private val schedule = date.flatMapLatest { today ->
        scheduleRepository.observeUpcoming(today).flatMapLatest { entries ->
            val todays = entries.filter { it.date == today }
            val pending = todays.firstOrNull { !it.done }
            val upcoming = entries.filter { it.date > today }
            val day = pending?.planDayId?.let(planRepository::observeDay) ?: flowOf(null)
            day.map { Schedule(today, pending, it, todays.isNotEmpty() && pending == null, upcoming) }
        }
    }

    private val dashboard = date.flatMapLatest { today ->
        val zone = TimeZone.currentSystemDefault()
        val since = windowStart(Dashboard.HEATMAP_WEEKS, today).atStartOfDayIn(zone)
        combine(
            historyRepository.observeFinishedSessionsBetween(since, Instant.DISTANT_FUTURE),
            historyRepository.observeFinishedCount(),
            profileRepository.observe(),
            preferencesRepository.preferences,
        ) { sessions, total, profile, prefs ->
            if (total == 0) return@combine null
            val days = sessions.map { TrainedDay(it.startedAt.toLocalDateTime(zone).date, WorkoutSummary.of(it).volumeKg) }
            Dashboard(
                today = today,
                weeks = weeklyTotals(days, Dashboard.HEATMAP_WEEKS, today),
                workoutsByDay = days.groupingBy { it.date }.eachCount(),
                weeklyGoal = profile?.daysPerWeek,
                streak = weekStreak(days.map { it.date }, today),
                totalWorkouts = total,
                unit = prefs.weightUnit,
            )
        }
    }

    // One plan subscription: the next day is derived inside the repository.
    val uiState: StateFlow<TodayUiState> = combine(
        planRepository.observePlanAndNextDay(),
        workoutRepository.observeActiveSession(),
        preferencesRepository.preferences,
        schedule,
        dashboard,
    ) { (plan, next), active, prefs, schedule, dashboard ->
        TodayUiState(
            loading = false,
            today = schedule.date,
            hasPlan = plan != null,
            nextDay = next,
            activeSessionId = active?.id,
            deloadUntil = prefs.deloadUntil?.takeIf { prefs.isDeload(schedule.date) },
            scheduledToday = schedule.today,
            scheduledDay = schedule.day,
            scheduledTodayDone = schedule.todayDone,
            upcoming = schedule.upcoming,
            dashboard = dashboard,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /**
     * Starts today's scheduled workout if there is one, otherwise the plan's next day.
     * [readiness] and [lighter] come from the check-in; null leaves them unset.
     */
    fun startNext(readiness: Int? = null, lighter: Boolean? = null, onStarted: (Long) -> Unit) = viewModelScope.launchReporting(errors, Failure.ACTION) {
        val state = uiState.value
        val scheduled = state.scheduledToday
        when {
            scheduled != null -> onStarted(scheduleRepository.start(scheduled, readiness, lighter))
            state.nextDay != null -> onStarted(workoutRepository.startPlannedWorkout(state.nextDay.id, readiness, lighter))
        }
    }

    fun startEmptyWorkout(onStarted: (Long) -> Unit) = viewModelScope.launchReporting(errors, Failure.ACTION) {
        onStarted(workoutRepository.startEmptyWorkout())
    }
}
