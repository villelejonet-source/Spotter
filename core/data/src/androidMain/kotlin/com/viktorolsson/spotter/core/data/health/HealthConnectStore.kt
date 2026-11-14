package com.viktorolsson.spotter.core.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import com.viktorolsson.spotter.core.model.HealthAvailability
import com.viktorolsson.spotter.core.model.HealthWorkout
import java.time.ZoneId
import kotlin.time.toJavaInstant

/**
 * Health Connect, Android's on-device health store that Fitbit, Samsung Health and others
 * read: part of the system from Android 14, an app from Google Play before that. Android-only
 * because Health Connect is; iOS would do the same against HealthKit.
 *
 * A workout is two records, a strength-training session and its active calories, both keyed
 * by the session's sync id, so writing one again replaces it instead of adding another.
 */
class HealthConnectStore(private val context: Context) : HealthStore {
    // Made on first use: creating it fails on a phone without Health Connect.
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_INSTALL
        else -> HealthAvailability.UNSUPPORTED
    }

    override suspend fun canWriteWorkouts(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(WRITE_PERMISSIONS)

    override suspend fun writeWorkout(workout: HealthWorkout) {
        client.insertRecords(workout.toRecords(ZoneId.systemDefault()))
        // Inserting only replaces what's written now, so calories from an earlier version go by hand.
        if (workout.activeKcal == null) client.deleteRecords(ActiveCaloriesBurnedRecord::class, emptyList(), listOf(workout.id))
    }

    override suspend fun deleteWorkout(id: String) {
        client.deleteRecords(ExerciseSessionRecord::class, emptyList(), listOf(id))
        client.deleteRecords(ActiveCaloriesBurnedRecord::class, emptyList(), listOf(id))
    }

    companion object {
        /** What the person is asked to allow: saving workouts and their calories, nothing read. */
        val WRITE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        )

        /** Health Connect's own permission screen; the result is the permissions now granted. */
        fun permissionRequest(): ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()

        /**
         * Health Connect's screen for this phone: where access is managed when it's installed,
         * its Play Store page (which continues into its setup) when it isn't.
         */
        fun openIntent(context: Context): Intent =
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getHealthConnectManageDataIntent(context)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
                    .setPackage("com.android.vending")
                    .putExtra("overlay", true)
                    .putExtra("callerId", context.packageName)
            }

        private const val PROVIDER = "com.google.android.apps.healthdata"
    }
}

/** The workout's records, in [zone] (the phone's, since a workout is logged where it was done). */
internal fun HealthWorkout.toRecords(zone: ZoneId): List<Record> {
    val startTime = start.toJavaInstant()
    val endTime = end.toJavaInstant()
    val startOffset = zone.rules.getOffset(startTime)
    val endOffset = zone.rules.getOffset(endTime)
    // Logged by hand on the phone, not measured by a sensor.
    val metadata = Metadata.manualEntry(clientRecordId = id, clientRecordVersion = version)
    return listOfNotNull<Record>(
        ExerciseSessionRecord(
            startTime = startTime,
            startZoneOffset = startOffset,
            endTime = endTime,
            endZoneOffset = endOffset,
            metadata = metadata,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = title,
        ),
        activeKcal?.let {
            ActiveCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                energy = Energy.kilocalories(it.toDouble()),
                metadata = metadata,
            )
        },
    )
}
