package com.viktorolsson.spotter.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.viktorolsson.spotter.R
import com.viktorolsson.spotter.feature.history.HistoryRoute
import com.viktorolsson.spotter.feature.progress.ProgressRoute
import com.viktorolsson.spotter.feature.settings.ProfileRoute
import com.viktorolsson.spotter.feature.today.TodayRoute

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    TODAY(TodayRoute, R.string.nav_today, Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter),
    HISTORY(HistoryRoute, R.string.nav_history, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    PROGRESS(ProgressRoute, R.string.nav_progress, Icons.Rounded.Insights, Icons.Outlined.Insights),
    PROFILE(ProfileRoute, R.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.Person),
}
