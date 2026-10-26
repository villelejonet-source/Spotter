package com.viktorolsson.spotter.shell

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
import org.jetbrains.compose.resources.StringResource
import com.viktorolsson.spotter.feature.history.HistoryRoute
import com.viktorolsson.spotter.feature.progress.ProgressRoute
import com.viktorolsson.spotter.feature.settings.ProfileRoute
import com.viktorolsson.spotter.feature.today.TodayRoute
import com.viktorolsson.spotter.shell.resources.Res
import com.viktorolsson.spotter.shell.resources.nav_history
import com.viktorolsson.spotter.shell.resources.nav_profile
import com.viktorolsson.spotter.shell.resources.nav_progress
import com.viktorolsson.spotter.shell.resources.nav_today

enum class TopLevelDestination(
    val route: Any,
    val label: StringResource,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    TODAY(TodayRoute, Res.string.nav_today, Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter),
    HISTORY(HistoryRoute, Res.string.nav_history, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    PROGRESS(ProgressRoute, Res.string.nav_progress, Icons.Rounded.Insights, Icons.Outlined.Insights),
    PROFILE(ProfileRoute, Res.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.Person),
}
