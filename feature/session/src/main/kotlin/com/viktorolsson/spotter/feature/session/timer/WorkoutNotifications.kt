package com.viktorolsson.spotter.feature.session.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.viktorolsson.spotter.core.data.repository.ActiveSession
import com.viktorolsson.spotter.core.model.RestTimer
import com.viktorolsson.spotter.feature.session.R

internal object WorkoutNotifications {
    const val ONGOING_ID = 1001
    private const val REST_DONE_ID = 1002
    private const val CHANNEL_WORKOUT = "workout"
    private const val CHANNEL_REST_DONE = "rest_done"
    private val VIBRATION = longArrayOf(0, 400, 200, 400)

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WORKOUT,
                context.getString(R.string.notif_channel_workout),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REST_DONE,
                context.getString(R.string.notif_channel_rest_done),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                vibrationPattern = VIBRATION
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
                )
            },
        )
    }

    /** Shown for the instant between the service starting and the first state read. */
    fun placeholder(context: Context): Notification = base(context)
        .setContentTitle(context.getString(R.string.notif_workout_title))
        .build()

    fun inProgress(context: Context, session: ActiveSession): Notification = base(context)
        .setContentTitle(context.getString(R.string.notif_workout_title))
        .setWhen(session.startedAt.toEpochMilli())
        .setShowWhen(true)
        .setUsesChronometer(true)
        .build()

    fun resting(context: Context, rest: RestTimer): Notification = base(context)
        .setContentTitle(context.getString(R.string.notif_rest_title))
        .setWhen(rest.endsAt.toEpochMilli())
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .addAction(0, context.getString(R.string.notif_add_30), serviceAction(context, WorkoutService.ACTION_ADD_30))
        .addAction(0, context.getString(R.string.rest_skip), serviceAction(context, WorkoutService.ACTION_SKIP))
        .build()

    fun postRestFinished(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REST_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.notif_rest_done_title))
            .setContentText(context.getString(R.string.notif_rest_done_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(VIBRATION)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .setContentIntent(openApp(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(REST_DONE_ID, notification)
    }

    fun update(context: Context, notification: Notification) {
        context.getSystemService(NotificationManager::class.java).notify(ONGOING_ID, notification)
    }

    private fun base(context: Context) = NotificationCompat.Builder(context, CHANNEL_WORKOUT)
        .setSmallIcon(R.drawable.ic_stat_timer)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp(context))

    private fun openApp(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun serviceAction(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, WorkoutService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
