package com.viktorolsson.spotter.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

data class Recommendation(
    val id: Long,
    val type: RecommendationType,
    val exerciseId: String?,
    val payload: RecommendationPayload,
    val createdAt: Instant,
    val status: RecommendationStatus,
)

/** What "Apply to plan" does, plus the evidence the card shows to explain why. */
@Serializable
data class RecommendationPayload(
    val action: RecommendationAction,
    val evidence: RecommendationEvidence,
) {
    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(text: String): RecommendationPayload = json.decodeFromString(text)
    }
}

@Serializable
sealed interface RecommendationAction {
    /** Adds an exercise to a plan day (weak-point accessory, or more frequency for a muscle). */
    @Serializable
    @SerialName("add_exercise")
    data class AddExercise(
        val planDayId: Long,
        val dayName: String,
        val exerciseId: String,
        val exerciseName: String,
        val sets: Int,
        val repMin: Int,
        val repMax: Int,
        val targetRir: Int?,
        val restSeconds: Int,
    ) : RecommendationAction

    /** One more set on each listed plan exercise. */
    @Serializable
    @SerialName("add_sets")
    data class AddSets(val planExerciseIds: List<Long>, val exerciseNames: List<String>) : RecommendationAction

    @Serializable
    @SerialName("change_rep_range")
    data class ChangeRepRange(
        val planExerciseId: Long,
        val fromSets: Int,
        val fromRepMin: Int,
        val fromRepMax: Int,
        val sets: Int,
        val repMin: Int,
        val repMax: Int,
    ) : RecommendationAction

    /** Replaces a plan exercise with a variation; [returning] marks the swap back afterwards. */
    @Serializable
    @SerialName("swap_variation")
    data class SwapVariation(
        val planExerciseId: Long,
        val fromExerciseId: String,
        val fromName: String,
        val toExerciseId: String,
        val toName: String,
        val weeks: Int,
        val returning: Boolean = false,
    ) : RecommendationAction

    /** A lighter week: fewer sets and ~10 % less weight in planned workouts. */
    @Serializable
    @SerialName("deload")
    data class Deload(val days: Int = 7) : RecommendationAction
}

enum class DeloadReason { SEVERAL_PLATEAUS, RECOVERY, SCHEDULED }

@Serializable
data class RecommendationEvidence(
    val exerciseName: String? = null,
    /** Latest best set of the stalled lift. */
    val weightKg: Double? = null,
    val reps: Int? = null,
    /** Sessions and weeks without progress. */
    val sessions: Int? = null,
    val weeks: Int? = null,
    val missedTwice: Boolean = false,
    val group: MuscleGroup? = null,
    val groupSets: Double? = null,
    val targetLow: Int? = null,
    val targetHigh: Int? = null,
    val deloadReason: DeloadReason? = null,
    val lifts: List<String> = emptyList(),
)
