package com.viktorolsson.spotter.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.viktorolsson.spotter.core.model.Readiness
import com.viktorolsson.spotter.core.model.ReadinessLevel
import com.viktorolsson.spotter.core.model.ReadinessReason
import com.viktorolsson.spotter.core.model.RecoverySignal
import com.viktorolsson.spotter.core.model.SessionAdvice
import com.viktorolsson.spotter.core.ui.component.ContentCard
import com.viktorolsson.spotter.core.ui.component.Eyebrow
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.today.resources.*
import com.viktorolsson.spotter.feature.today.resources.Res
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/** This morning's readiness above the workout: the level, why, and what it suggests for today. */
@Composable
internal fun ReadinessCard(readiness: Readiness) {
    ContentCard(Modifier.fillMaxWidth()) {
        Eyebrow(stringResource(Res.string.readiness_label))
        Text(readinessTitle(readiness.level), style = MaterialTheme.typography.titleMedium)
        Text(readinessSummary(readiness), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (readiness.advice != SessionAdvice.AS_PLANNED) Text(adviceText(readiness.advice), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun readinessTitle(level: ReadinessLevel): String = stringResource(
    when (level) {
        ReadinessLevel.GOOD -> Res.string.readiness_good
        ReadinessLevel.REDUCED -> Res.string.readiness_reduced
        ReadinessLevel.LOW -> Res.string.readiness_low
        ReadinessLevel.VERY_LOW -> Res.string.readiness_very_low
    },
)

@Composable
internal fun adviceText(advice: SessionAdvice): String = stringResource(
    when (advice) {
        SessionAdvice.AS_PLANNED -> Res.string.readiness_advice_as_planned
        SessionAdvice.LIGHTER -> Res.string.readiness_advice_lighter
        SessionAdvice.REST_OR_LIGHT -> Res.string.readiness_advice_rest
    },
)

/** Why, in plain words: each reason as a sentence, or how long last night was when nothing stands out. */
@Composable
internal fun readinessSummary(readiness: Readiness): String {
    val reasons = readiness.reasons.map { reason ->
        when (reason) {
            is ReadinessReason.ShortSleep -> reason.underUsual
                ?.let { stringResource(Res.string.readiness_sleep_under, hoursAndMinutes(reason.slept), hoursAndMinutes(it)) }
                ?: stringResource(Res.string.readiness_sleep_short, hoursAndMinutes(reason.slept))
            is ReadinessReason.SleepDebt -> stringResource(Res.string.readiness_sleep_debt, hoursAndMinutes(reason.underUsualPerNight))
            is ReadinessReason.LowHrv -> stringResource(Res.string.readiness_hrv_low, reason.percentUnderUsual)
            is ReadinessReason.HighRestingHeartRate -> stringResource(Res.string.readiness_resting_high, reason.bpmOverUsual)
        }
    }
    val found = when {
        reasons.isNotEmpty() -> reasons.joinToString(" ")
        else -> readiness.slept?.let { stringResource(Res.string.readiness_normal_slept, hoursAndMinutes(it)) }
            ?: stringResource(Res.string.readiness_normal)
    }
    if (readiness.missing.isEmpty()) return found
    // The picture is partial: say what it's missing, so "nothing stands out" isn't read as more than it is.
    val missing = readiness.missing.sorted().map { signal ->
        stringResource(
            when (signal) {
                RecoverySignal.SLEEP -> Res.string.readiness_signal_sleep
                RecoverySignal.HRV -> Res.string.readiness_signal_hrv
                RecoverySignal.RESTING_HEART_RATE -> Res.string.readiness_signal_resting
            },
        )
    }
    return found + " " + stringResource(Res.string.readiness_missing, missing.joinToString(", "))
}

/** "5 h 10 min", or "50 min" under an hour. */
@Composable
private fun hoursAndMinutes(duration: Duration): String {
    val minutes = duration.inWholeMinutes
    return if (minutes < 60) {
        stringResource(Res.string.readiness_minutes, minutes.toInt())
    } else {
        stringResource(Res.string.readiness_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

/** Offers reading sleep and recovery, once: set up in Profile, where the switch and its explanation are. */
@Composable
internal fun HealthIntroCard(onSetUp: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(Res.string.health_intro_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(stringResource(Res.string.health_intro_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.health_intro_dismiss)) }
                Button(onClick = onSetUp, modifier = Modifier.padding(start = Spacing.sm)) { Text(stringResource(Res.string.health_intro_set_up)) }
            }
        }
    }
}
