// Generated from the core:model enums; keep in sync when adding values.
package com.viktorolsson.spotter.core.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.*

val Muscle.labelRes: StringResource
    get() = when (this) {
        Muscle.CHEST -> Res.string.muscle_chest
        Muscle.FRONT_DELTS -> Res.string.muscle_front_delts
        Muscle.SIDE_DELTS -> Res.string.muscle_side_delts
        Muscle.REAR_DELTS -> Res.string.muscle_rear_delts
        Muscle.TRICEPS -> Res.string.muscle_triceps
        Muscle.BICEPS -> Res.string.muscle_biceps
        Muscle.FOREARMS -> Res.string.muscle_forearms
        Muscle.LATS -> Res.string.muscle_lats
        Muscle.UPPER_BACK -> Res.string.muscle_upper_back
        Muscle.TRAPS -> Res.string.muscle_traps
        Muscle.LOWER_BACK -> Res.string.muscle_lower_back
        Muscle.ABS -> Res.string.muscle_abs
        Muscle.OBLIQUES -> Res.string.muscle_obliques
        Muscle.GLUTES -> Res.string.muscle_glutes
        Muscle.QUADS -> Res.string.muscle_quads
        Muscle.HAMSTRINGS -> Res.string.muscle_hamstrings
        Muscle.ADDUCTORS -> Res.string.muscle_adductors
        Muscle.ABDUCTORS -> Res.string.muscle_abductors
        Muscle.CALVES -> Res.string.muscle_calves
    }

val Equipment.labelRes: StringResource
    get() = when (this) {
        Equipment.BARBELL -> Res.string.equipment_barbell
        Equipment.EZ_BAR -> Res.string.equipment_ez_bar
        Equipment.TRAP_BAR -> Res.string.equipment_trap_bar
        Equipment.DUMBBELL -> Res.string.equipment_dumbbell
        Equipment.KETTLEBELL -> Res.string.equipment_kettlebell
        Equipment.CABLE -> Res.string.equipment_cable
        Equipment.MACHINE -> Res.string.equipment_machine
        Equipment.SMITH_MACHINE -> Res.string.equipment_smith_machine
        Equipment.BENCH -> Res.string.equipment_bench
        Equipment.SQUAT_RACK -> Res.string.equipment_squat_rack
        Equipment.PULL_UP_BAR -> Res.string.equipment_pull_up_bar
        Equipment.DIP_STATION -> Res.string.equipment_dip_station
        Equipment.RESISTANCE_BAND -> Res.string.equipment_resistance_band
        Equipment.LANDMINE -> Res.string.equipment_landmine
    }

val MovementPattern.labelRes: StringResource
    get() = when (this) {
        MovementPattern.HORIZONTAL_PUSH -> Res.string.pattern_horizontal_push
        MovementPattern.VERTICAL_PUSH -> Res.string.pattern_vertical_push
        MovementPattern.HORIZONTAL_PULL -> Res.string.pattern_horizontal_pull
        MovementPattern.VERTICAL_PULL -> Res.string.pattern_vertical_pull
        MovementPattern.SQUAT -> Res.string.pattern_squat
        MovementPattern.HINGE -> Res.string.pattern_hinge
        MovementPattern.LUNGE -> Res.string.pattern_lunge
        MovementPattern.HIP_THRUST -> Res.string.pattern_hip_thrust
        MovementPattern.CARRY -> Res.string.pattern_carry
        MovementPattern.CHEST_FLY -> Res.string.pattern_chest_fly
        MovementPattern.LATERAL_RAISE -> Res.string.pattern_lateral_raise
        MovementPattern.REAR_DELT_FLY -> Res.string.pattern_rear_delt_fly
        MovementPattern.SHRUG -> Res.string.pattern_shrug
        MovementPattern.ELBOW_FLEXION -> Res.string.pattern_elbow_flexion
        MovementPattern.ELBOW_EXTENSION -> Res.string.pattern_elbow_extension
        MovementPattern.KNEE_EXTENSION -> Res.string.pattern_knee_extension
        MovementPattern.KNEE_FLEXION -> Res.string.pattern_knee_flexion
        MovementPattern.HIP_ABDUCTION -> Res.string.pattern_hip_abduction
        MovementPattern.HIP_ADDUCTION -> Res.string.pattern_hip_adduction
        MovementPattern.CALF_RAISE -> Res.string.pattern_calf_raise
        MovementPattern.CORE_ANTI_EXTENSION -> Res.string.pattern_core_anti_extension
        MovementPattern.CORE_FLEXION -> Res.string.pattern_core_flexion
        MovementPattern.CORE_ROTATION -> Res.string.pattern_core_rotation
    }

val BodyArea.labelRes: StringResource
    get() = when (this) {
        BodyArea.CHEST -> Res.string.body_area_chest
        BodyArea.BACK -> Res.string.body_area_back
        BodyArea.SHOULDERS -> Res.string.body_area_shoulders
        BodyArea.ARMS -> Res.string.body_area_arms
        BodyArea.LEGS -> Res.string.body_area_legs
        BodyArea.GLUTES -> Res.string.body_area_glutes
        BodyArea.CORE -> Res.string.body_area_core
    }

val MuscleGroup.labelRes: StringResource
    get() = when (this) {
        MuscleGroup.CHEST -> Res.string.muscle_group_chest
        MuscleGroup.BACK -> Res.string.muscle_group_back
        MuscleGroup.SHOULDERS -> Res.string.muscle_group_shoulders
        MuscleGroup.BICEPS -> Res.string.muscle_group_biceps
        MuscleGroup.TRICEPS -> Res.string.muscle_group_triceps
        MuscleGroup.QUADS -> Res.string.muscle_group_quads
        MuscleGroup.HAMSTRINGS -> Res.string.muscle_group_hamstrings
        MuscleGroup.GLUTES -> Res.string.muscle_group_glutes
        MuscleGroup.CALVES -> Res.string.muscle_group_calves
        MuscleGroup.CORE -> Res.string.muscle_group_core
    }

val PersonalRecordType.labelRes: StringResource
    get() = when (this) {
        PersonalRecordType.ESTIMATED_1RM -> Res.string.record_estimated_1rm
        PersonalRecordType.REPS_AT_WEIGHT -> Res.string.record_reps_at_weight
        PersonalRecordType.VOLUME -> Res.string.record_volume
    }

/** "RIR" or "RPE" as a column header; empty when effort isn't rated. */
@Composable
fun EffortScale.label(): String = when (this) {
    EffortScale.OFF -> ""
    EffortScale.RIR -> stringResource(Res.string.effort_scale_rir)
    EffortScale.RPE -> stringResource(Res.string.effort_scale_rpe)
}
