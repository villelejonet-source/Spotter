package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import com.viktorolsson.spotter.core.engine.MobilityDrill
import com.viktorolsson.spotter.core.ui.component.ExpandableCard
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.session.resources.*
import com.viktorolsson.spotter.feature.session.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Mobility drills for the start of the session, folded away by default. Ticks are for the
 * lifter's own tracking and aren't saved.
 */
@Composable
internal fun WarmUpPanel(
    drills: List<MobilityDrill>,
    done: Set<MobilityDrill>,
    onToggle: (MobilityDrill) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val ticked = drills.count { it in done }
    val summary = if (ticked == 0) {
        stringResource(Res.string.mobility_summary, drills.size, drills.size)
    } else {
        stringResource(Res.string.mobility_done, ticked, drills.size)
    }
    ExpandableCard(
        title = stringResource(Res.string.mobility_title),
        summary = summary,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        drills.forEach { drill -> DrillRow(drill, checked = drill in done, onToggle = { onToggle(drill) }) }
    }
}

@Composable
private fun DrillRow(drill: MobilityDrill, checked: Boolean, onToggle: () -> Unit) {
    val (name, cue) = drill.text()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${stringResource(name)} ${drill.dose()}",
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            Text(stringResource(cue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The row is the toggle; the box only shows its state.
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun MobilityDrill.dose(): String = when {
    timed -> stringResource(Res.string.mobility_seconds, amount)
    perSide -> stringResource(Res.string.mobility_reps_per_side, amount)
    else -> stringResource(Res.string.mobility_reps, amount)
}

private fun MobilityDrill.text(): Pair<StringResource, StringResource> = when (this) {
    MobilityDrill.JUMPING_JACKS -> Res.string.drill_jumping_jacks to Res.string.drill_jumping_jacks_cue
    MobilityDrill.INCHWORM -> Res.string.drill_inchworm to Res.string.drill_inchworm_cue
    MobilityDrill.ARM_CIRCLES -> Res.string.drill_arm_circles to Res.string.drill_arm_circles_cue
    MobilityDrill.SCAPULAR_PUSH_UP -> Res.string.drill_scapular_push_up to Res.string.drill_scapular_push_up_cue
    MobilityDrill.WALL_SLIDE -> Res.string.drill_wall_slide to Res.string.drill_wall_slide_cue
    MobilityDrill.PRONE_Y_RAISE -> Res.string.drill_prone_y_raise to Res.string.drill_prone_y_raise_cue
    MobilityDrill.THORACIC_ROTATION -> Res.string.drill_thoracic_rotation to Res.string.drill_thoracic_rotation_cue
    MobilityDrill.CROSS_BODY_ARM_SWING -> Res.string.drill_cross_body_arm_swing to Res.string.drill_cross_body_arm_swing_cue
    MobilityDrill.LEG_SWING -> Res.string.drill_leg_swing to Res.string.drill_leg_swing_cue
    MobilityDrill.HIP_90_90 -> Res.string.drill_hip_90_90 to Res.string.drill_hip_90_90_cue
    MobilityDrill.WORLDS_GREATEST_STRETCH -> Res.string.drill_worlds_greatest_stretch to Res.string.drill_worlds_greatest_stretch_cue
    MobilityDrill.BODYWEIGHT_SQUAT -> Res.string.drill_bodyweight_squat to Res.string.drill_bodyweight_squat_cue
    MobilityDrill.GLUTE_BRIDGE -> Res.string.drill_glute_bridge to Res.string.drill_glute_bridge_cue
    MobilityDrill.ANKLE_ROCK -> Res.string.drill_ankle_rock to Res.string.drill_ankle_rock_cue
    MobilityDrill.CAT_COW -> Res.string.drill_cat_cow to Res.string.drill_cat_cow_cue
    MobilityDrill.COSSACK_SQUAT -> Res.string.drill_cossack_squat to Res.string.drill_cossack_squat_cue
}
