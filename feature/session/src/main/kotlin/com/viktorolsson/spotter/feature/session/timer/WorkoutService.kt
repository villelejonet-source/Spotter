package com.viktorolsson.spotter.feature.session.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.viktorolsson.spotter.core.data.repository.RestTimerRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Runs while a workout is in progress. Shows the workout stopwatch in an ongoing
 * notification, switches it to a rest countdown (+30 s / Skip) while resting, and
 * alerts when rest ends, including with the screen off or another app open.
 * Stops itself when no workout is active.
 */
@AndroidEntryPoint
class WorkoutService : Service() {
    @Inject lateinit var workoutRepository: WorkoutRepository
    @Inject lateinit var restTimerRepository: RestTimerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WorkoutNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            WorkoutNotifications.ONGOING_ID,
            WorkoutNotifications.placeholder(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        when (intent?.action) {
            ACTION_ADD_30 -> scope.launch { restTimerRepository.adjust(30) }
            ACTION_SKIP -> scope.launch { restTimerRepository.stop() }
        }
        if (observer == null) observer = scope.launch { observe() }
        // Not sticky: the app restarts the service when opened with a workout still active.
        return START_NOT_STICKY
    }

    private suspend fun observe() {
        combine(workoutRepository.observeActiveSession(), restTimerRepository.restTimer, ::Pair)
            .collectLatest { (session, rest) ->
                if (session == null) {
                    releaseWakeLock()
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                val remaining = rest?.let { Duration.between(Instant.now(), it.endsAt) }
                if (rest != null && remaining != null && !remaining.isNegative && !remaining.isZero) {
                    WorkoutNotifications.update(this, WorkoutNotifications.resting(this, rest))
                    acquireWakeLock(remaining)
                    delay(remaining.toMillis())
                    // Alert before clearing: clearing re-emits and cancels this block.
                    WorkoutNotifications.postRestFinished(this)
                    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) vibrate()
                    releaseWakeLock()
                    restTimerRepository.stop()
                } else {
                    releaseWakeLock()
                    // A rest that ran out while the app was dead ends quietly.
                    if (rest != null) restTimerRepository.stop()
                    WorkoutNotifications.update(this, WorkoutNotifications.inProgress(this, session))
                }
            }
    }

    private fun acquireWakeLock(duration: Duration) {
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spotter:rest")
            .apply { acquire(duration.toMillis() + 10_000) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        internal const val ACTION_ADD_30 = "com.viktorolsson.spotter.action.REST_ADD_30"
        internal const val ACTION_SKIP = "com.viktorolsson.spotter.action.REST_SKIP"

        /** Safe to call repeatedly; call while the app is in the foreground. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            } catch (e: IllegalStateException) {
                // Background start not allowed (e.g. activity already stopped); the next resume retries.
                Log.w("WorkoutService", "Could not start workout service", e)
            }
        }
    }
}
