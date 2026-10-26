package com.viktorolsson.spotter.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.ScheduleRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.ScheduledWorkout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

data class TodayUiState(
    val loading: Boolean = true,
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
)

private data class Schedule(val today: ScheduledWorkout?, val day: PlanDay?, val todayDone: Boolean, val upcoming: List<ScheduledWorkout>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    planRepository: PlanRepository,
    private val workoutRepository: WorkoutRepository,
    private val scheduleRepository: ScheduleRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val schedule = scheduleRepository.observeUpcoming().flatMapLatest { entries ->
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val todays = entries.filter { it.date == today }
        val pending = todays.firstOrNull { !it.done }
        val upcoming = entries.filter { it.date > today }
        val day = pending?.planDayId?.let(planRepository::observeDay) ?: flowOf(null)
        day.map { Schedule(pending, it, todays.isNotEmpty() && pending == null, upcoming) }
    }

    // One plan subscription: the next day is derived inside the repository.
    val uiState: StateFlow<TodayUiState> = combine(
        planRepository.observePlanAndNextDay(),
        workoutRepository.observeActiveSession(),
        preferencesRepository.preferences,
        schedule,
    ) { (plan, next), active, prefs, schedule ->
        TodayUiState(
            loading = false,
            hasPlan = plan != null,
            nextDay = next,
            activeSessionId = active?.id,
            deloadUntil = prefs.deloadUntil?.takeIf { prefs.isDeload(Clock.System.todayIn(TimeZone.currentSystemDefault())) },
            scheduledToday = schedule.today,
            scheduledDay = schedule.day,
            scheduledTodayDone = schedule.todayDone,
            upcoming = schedule.upcoming,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** Starts today's scheduled workout if there is one, otherwise the plan's next day. */
    fun startNext(onStarted: (Long) -> Unit) = viewModelScope.launch {
        val state = uiState.value
        val scheduled = state.scheduledToday
        when {
            scheduled != null -> onStarted(scheduleRepository.start(scheduled))
            state.nextDay != null -> onStarted(workoutRepository.startPlannedWorkout(state.nextDay.id))
        }
    }

    fun startEmptyWorkout(onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(workoutRepository.startEmptyWorkout())
    }
}
