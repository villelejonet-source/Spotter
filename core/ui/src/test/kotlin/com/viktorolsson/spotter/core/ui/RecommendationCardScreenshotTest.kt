package com.viktorolsson.spotter.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.viktorolsson.spotter.core.model.DeloadReason
import com.viktorolsson.spotter.core.model.Recommendation
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationEvidence
import com.viktorolsson.spotter.core.model.RecommendationPayload
import com.viktorolsson.spotter.core.model.RecommendationStatus
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.ui.component.RecommendationCard
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RecommendationCardScreenshotTest {
    private fun rec(type: RecommendationType, action: RecommendationAction, evidence: RecommendationEvidence) =
        Recommendation(1, type, "barbell-bench-press", RecommendationPayload(action, evidence), Instant.EPOCH, RecommendationStatus.ACTIVE)

    private val accessory = rec(
        RecommendationType.ADD_ACCESSORY,
        RecommendationAction.AddExercise(1, "Upper A", "close-grip-bench-press", "Close-Grip Bench Press", 3, 6, 10, 2, 120),
        RecommendationEvidence(exerciseName = "Barbell Bench Press", weightKg = 80.0, reps = 5, sessions = 4, weeks = 3),
    )
    private val deload = rec(
        RecommendationType.DELOAD,
        RecommendationAction.Deload(),
        RecommendationEvidence(deloadReason = DeloadReason.SEVERAL_PLATEAUS, lifts = listOf("Bench", "Squat", "Row")),
    )

    private fun capture(name: String, dark: Boolean) = captureRoboImage("src/test/screenshots/$name.png") {
        SpotterTheme(darkTheme = dark, dynamicColor = false) {
            Surface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RecommendationCard(accessory, WeightUnit.KG, onApply = {}, onDismiss = {})
                    RecommendationCard(deload, WeightUnit.KG, onApply = {}, onDismiss = {})
                }
            }
        }
    }

    @Test
    fun recommendationCards_light() = capture("recommendation_cards_light", dark = false)

    @Test
    fun recommendationCards_dark() = capture("recommendation_cards_dark", dark = true)
}
