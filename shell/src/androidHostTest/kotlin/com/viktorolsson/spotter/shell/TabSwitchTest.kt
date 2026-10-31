package com.viktorolsson.spotter.shell

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.feature.history.HistoryRoute
import com.viktorolsson.spotter.feature.plan.PlanRoute
import com.viktorolsson.spotter.feature.today.TodayRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switching bottom tabs must not animate: during a crossfade both tabs are on screen, so the
 * old one shows behind the new one, and a quick second tap lands mid-transition (the iOS
 * report). Opening a screen from a tab keeps its transition. The clock is driven by hand.
 * With the old 700 ms fade the previous tab stayed for about 47 frames; without it Navigation
 * still takes a few frames to drop the old entry, so each check is 100 ms after the tap.
 */
@RunWith(AndroidJUnit4::class)
class TabSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun showApp() {
        compose.setContent {
            nav = rememberNavController()
            SpotterNavHost(nav) {
                composable<TodayRoute> { Text("Today tab") }
                composable<HistoryRoute> { Text("History tab") }
                composable<PlanRoute> { Text("Plan screen") }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advanceAMoment() = compose.mainClock.advanceTimeBy(100)

    @Test
    fun switchingTabsShowsOnlyTheNewTabAMomentLater() {
        showApp()
        compose.runOnUiThread { nav.navigateToTab(TopLevelDestination.HISTORY) }
        advanceAMoment()

        compose.onNodeWithText("History tab").assertExists()
        compose.onNodeWithText("Today tab").assertDoesNotExist()
    }

    @Test
    fun switchingBackAndForthQuicklyEndsOnTheLastTabAlone() {
        showApp()
        compose.runOnUiThread { nav.navigateToTab(TopLevelDestination.HISTORY) }
        // Back again while the old fade would still be running.
        compose.mainClock.advanceTimeBy(300)
        compose.runOnUiThread { nav.navigateToTab(TopLevelDestination.TODAY) }
        advanceAMoment()

        compose.onNodeWithText("Today tab").assertExists()
        compose.onNodeWithText("History tab").assertDoesNotExist()
    }

    @Test
    fun openingAScreenFromATabStillFadesIn() {
        showApp()
        compose.runOnUiThread { nav.navigate(PlanRoute) }
        advanceAMoment()

        // Mid-fade: the tab is still there under the screen coming in.
        compose.onNodeWithText("Plan screen").assertExists()
        compose.onNodeWithText("Today tab").assertExists()
    }
}
