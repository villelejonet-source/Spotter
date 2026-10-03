package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.DeloadReason
import com.viktorolsson.spotter.core.model.Recommendation
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationEvidence
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.ui.R
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.labelRes

/** A recommendation with the evidence behind it, and Apply / Dismiss. */
@Composable
fun RecommendationCard(
    recommendation: Recommendation,
    unit: WeightUnit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val evidence = recommendation.payload.evidence
    val action = recommendation.payload.action
    val (icon, title, body) = describe(action, evidence, unit)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.rec_dismiss)) }
                Button(onClick = onApply, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(if (action is RecommendationAction.Deload) R.string.rec_apply_deload else R.string.rec_apply))
                }
            }
        }
    }
}

@Composable
private fun describe(action: RecommendationAction, evidence: RecommendationEvidence, unit: WeightUnit): Triple<ImageVector, String, String> {
    val why = plateauEvidence(evidence, unit)
    fun join(vararg parts: String?) = parts.filterNotNull().joinToString(" ")
    return when (action) {
        is RecommendationAction.AddExercise -> if (evidence.group != null && evidence.exerciseName == null) {
            val group = stringResource(evidence.group!!.labelRes)
            Triple(
                Icons.Rounded.CalendarMonth,
                stringResource(R.string.rec_frequency_title, group.lowercase()),
                stringResource(R.string.rec_frequency_body, group, action.exerciseName, action.sets, action.repMin, action.repMax, action.dayName),
            )
        } else {
            Triple(
                Icons.Rounded.Add,
                stringResource(R.string.rec_accessory_title, action.exerciseName),
                join(why, stringResource(R.string.rec_accessory_body, action.exerciseName, action.sets, action.repMin, action.repMax, action.dayName)),
            )
        }
        is RecommendationAction.AddSets -> {
            val group = stringResource(evidence.group!!.labelRes)
            Triple(
                Icons.AutoMirrored.Rounded.TrendingUp,
                stringResource(R.string.rec_volume_title, group.lowercase()),
                join(
                    why,
                    stringResource(
                        R.string.rec_volume_body,
                        group,
                        evidence.groupSets?.let { if (it % 1.0 == 0.0) "${it.toInt()}" else "%.1f".format(it) } ?: "0",
                        evidence.targetLow ?: 0,
                        evidence.targetHigh ?: 0,
                        action.exerciseNames.joinToString(" and "),
                    ),
                ),
            )
        }
        is RecommendationAction.ChangeRepRange -> Triple(
            Icons.Rounded.Tune,
            stringResource(R.string.rec_range_title, evidence.exerciseName.orEmpty()),
            join(
                why,
                stringResource(
                    R.string.rec_range_body, action.fromSets, action.fromRepMin, action.fromRepMax, evidence.weeks ?: 0,
                    action.sets, action.repMin, action.repMax,
                ),
            ),
        )
        is RecommendationAction.SwapVariation -> if (action.returning) {
            Triple(
                Icons.Rounded.SwapHoriz,
                stringResource(R.string.rec_return_title, action.toName),
                stringResource(R.string.rec_return_body, action.fromName, action.weeks, action.toName),
            )
        } else {
            Triple(
                Icons.Rounded.SwapHoriz,
                stringResource(R.string.rec_variation_title, action.fromName, action.toName),
                join(why, stringResource(R.string.rec_variation_body, action.toName, action.weeks)),
            )
        }
        is RecommendationAction.Deload -> {
            val reason = when (evidence.deloadReason) {
                DeloadReason.SEVERAL_PLATEAUS -> stringResource(R.string.rec_deload_plateaus, evidence.lifts.size, evidence.lifts.joinToString(", "))
                DeloadReason.RECOVERY -> stringResource(R.string.rec_deload_recovery, evidence.lifts.joinToString(", "))
                DeloadReason.SCHEDULED, null -> stringResource(R.string.rec_deload_scheduled, evidence.weeks ?: 0)
            }
            Triple(Icons.Rounded.BatteryChargingFull, stringResource(R.string.rec_deload_title), join(reason, stringResource(R.string.rec_deload_body)))
        }
    }
}

/** "Bench hasn't progressed in 4 sessions over 3 weeks (latest best 80 kg × 5)." */
@Composable
private fun plateauEvidence(evidence: RecommendationEvidence, unit: WeightUnit): String? {
    val name = evidence.exerciseName ?: return null
    val best = formatSet(evidence.weightKg, evidence.reps, unit)
    return if (evidence.missedTwice) {
        stringResource(R.string.rec_evidence_missed, name, best)
    } else {
        stringResource(R.string.rec_evidence_stalled, name, evidence.sessions ?: 0, evidence.weeks ?: 0, best)
    }
}
