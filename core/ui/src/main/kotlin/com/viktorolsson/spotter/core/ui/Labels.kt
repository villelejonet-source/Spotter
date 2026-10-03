// Generated from the core:model enums; keep in sync when adding values.
package com.viktorolsson.spotter.core.ui

import androidx.annotation.StringRes
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.Muscle

val Muscle.labelRes: Int
    @StringRes get() = when (this) {
        Muscle.CHEST -> R.string.muscle_chest
        Muscle.FRONT_DELTS -> R.string.muscle_front_delts
        Muscle.SIDE_DELTS -> R.string.muscle_side_delts
        Muscle.REAR_DELTS -> R.string.muscle_rear_delts
        Muscle.TRICEPS -> R.string.muscle_triceps
        Muscle.BICEPS -> R.string.muscle_biceps
        Muscle.FOREARMS -> R.string.muscle_forearms
        Muscle.LATS -> R.string.muscle_lats
        Muscle.UPPER_BACK -> R.string.muscle_upper_back
        Muscle.TRAPS -> R.string.muscle_traps
        Muscle.LOWER_BACK -> R.string.muscle_lower_back
        Muscle.ABS -> R.string.muscle_abs
        Muscle.OBLIQUES -> R.string.muscle_obliques
        Muscle.GLUTES -> R.string.muscle_glutes
        Muscle.QUADS -> R.string.muscle_quads
        Muscle.HAMSTRINGS -> R.string.muscle_hamstrings
        Muscle.ADDUCTORS -> R.string.muscle_adductors
        Muscle.ABDUCTORS -> R.string.muscle_abductors
        Muscle.CALVES -> R.string.muscle_calves
    }

val Equipment.labelRes: Int
    @StringRes get() = when (this) {
        Equipment.BARBELL -> R.string.equipment_barbell
        Equipment.EZ_BAR -> R.string.equipment_ez_bar
        Equipment.TRAP_BAR -> R.string.equipment_trap_bar
        Equipment.DUMBBELL -> R.string.equipment_dumbbell
        Equipment.KETTLEBELL -> R.string.equipment_kettlebell
        Equipment.CABLE -> R.string.equipment_cable
        Equipment.MACHINE -> R.string.equipment_machine
        Equipment.SMITH_MACHINE -> R.string.equipment_smith_machine
        Equipment.BENCH -> R.string.equipment_bench
        Equipment.SQUAT_RACK -> R.string.equipment_squat_rack
        Equipment.PULL_UP_BAR -> R.string.equipment_pull_up_bar
        Equipment.DIP_STATION -> R.string.equipment_dip_station
        Equipment.RESISTANCE_BAND -> R.string.equipment_resistance_band
        Equipment.LANDMINE -> R.string.equipment_landmine
    }

val MovementPattern.labelRes: Int
    @StringRes get() = when (this) {
        MovementPattern.HORIZONTAL_PUSH -> R.string.pattern_horizontal_push
        MovementPattern.VERTICAL_PUSH -> R.string.pattern_vertical_push
        MovementPattern.HORIZONTAL_PULL -> R.string.pattern_horizontal_pull
        MovementPattern.VERTICAL_PULL -> R.string.pattern_vertical_pull
        MovementPattern.SQUAT -> R.string.pattern_squat
        MovementPattern.HINGE -> R.string.pattern_hinge
        MovementPattern.LUNGE -> R.string.pattern_lunge
        MovementPattern.HIP_THRUST -> R.string.pattern_hip_thrust
        MovementPattern.CARRY -> R.string.pattern_carry
        MovementPattern.CHEST_FLY -> R.string.pattern_chest_fly
        MovementPattern.LATERAL_RAISE -> R.string.pattern_lateral_raise
        MovementPattern.REAR_DELT_FLY -> R.string.pattern_rear_delt_fly
        MovementPattern.SHRUG -> R.string.pattern_shrug
        MovementPattern.ELBOW_FLEXION -> R.string.pattern_elbow_flexion
        MovementPattern.ELBOW_EXTENSION -> R.string.pattern_elbow_extension
        MovementPattern.KNEE_EXTENSION -> R.string.pattern_knee_extension
        MovementPattern.KNEE_FLEXION -> R.string.pattern_knee_flexion
        MovementPattern.HIP_ABDUCTION -> R.string.pattern_hip_abduction
        MovementPattern.HIP_ADDUCTION -> R.string.pattern_hip_adduction
        MovementPattern.CALF_RAISE -> R.string.pattern_calf_raise
        MovementPattern.CORE_ANTI_EXTENSION -> R.string.pattern_core_anti_extension
        MovementPattern.CORE_FLEXION -> R.string.pattern_core_flexion
        MovementPattern.CORE_ROTATION -> R.string.pattern_core_rotation
    }

val BodyArea.labelRes: Int
    @StringRes get() = when (this) {
        BodyArea.CHEST -> R.string.body_area_chest
        BodyArea.BACK -> R.string.body_area_back
        BodyArea.SHOULDERS -> R.string.body_area_shoulders
        BodyArea.ARMS -> R.string.body_area_arms
        BodyArea.LEGS -> R.string.body_area_legs
        BodyArea.GLUTES -> R.string.body_area_glutes
        BodyArea.CORE -> R.string.body_area_core
    }

val MuscleGroup.labelRes: Int
    @StringRes get() = when (this) {
        MuscleGroup.CHEST -> R.string.muscle_group_chest
        MuscleGroup.BACK -> R.string.muscle_group_back
        MuscleGroup.SHOULDERS -> R.string.muscle_group_shoulders
        MuscleGroup.BICEPS -> R.string.muscle_group_biceps
        MuscleGroup.TRICEPS -> R.string.muscle_group_triceps
        MuscleGroup.QUADS -> R.string.muscle_group_quads
        MuscleGroup.HAMSTRINGS -> R.string.muscle_group_hamstrings
        MuscleGroup.GLUTES -> R.string.muscle_group_glutes
        MuscleGroup.CALVES -> R.string.muscle_group_calves
        MuscleGroup.CORE -> R.string.muscle_group_core
    }

val PersonalRecordType.labelRes: Int
    @StringRes get() = when (this) {
        PersonalRecordType.ESTIMATED_1RM -> R.string.record_estimated_1rm
        PersonalRecordType.REPS_AT_WEIGHT -> R.string.record_reps_at_weight
        PersonalRecordType.VOLUME -> R.string.record_volume
    }
