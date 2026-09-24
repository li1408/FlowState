package com.markel.flowstate.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.markel.flowstate.core.notifications.R

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "task_reminders"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Shows a reminder notification with a "Complete" action.
     * Tapping the notification opens MainActivity; tapping "Complete" marks the task as done.
     * [notificationId] should be the task ID so each task has its own notification slot
     * (a new reminder for the same task replaces the old one automatically).
     */
    fun showReminder(
        notificationId: Int,
        taskTitle: String,
        taskDescription: String?,
        completePendingIntent: PendingIntent
    ) {
        val tapIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }

        val tapPendingIntent = tapIntent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.new_ic_launcher_foreground_white)
            .setContentTitle(taskTitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)

        // Show description if exists
        if (!taskDescription.isNullOrBlank()) {
            builder.setContentText(taskDescription)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(taskDescription))
        }

        // Add action to complete the task
        builder.addAction(
            R.drawable.check_24px,
            context.getString(R.string.notification_action_complete),
            completePendingIntent
        )

        notificationManager.notify(notificationId, builder.build())
    }
}
