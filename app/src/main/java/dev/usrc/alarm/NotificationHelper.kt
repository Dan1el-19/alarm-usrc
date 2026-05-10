package dev.usrc.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val REMINDER_CHANNEL_ID = "reminder_channel"
        const val ALARM_CHANNEL_ID = "alarm_channel"
        const val REMINDER_ID = 100
        const val ALARM_ID = 200
    }

    fun createNotificationChannels() {
        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(null, null) // Use default or specific if needed
        }
        notificationManager.createNotificationChannel(reminderChannel)
        notificationManager.createNotificationChannel(alarmChannel)
    }

    fun showReminderNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(REMINDER_ID, notification)
    }

    fun showReminderNotificationWithActions(title: String, message: String, variants: List<AlarmVariant>, startIndex: Int = 0) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(true) // Prevent accidental swipe away while deciding

        // Show up to 2 variants and a "Next" button
        val listSize = variants.size
        if (listSize > 0) {
            val actualStartIndex = startIndex % listSize
            val variantsToShow = if (listSize <= 3) variants else {
                 listOf(
                     variants[actualStartIndex],
                     variants[(actualStartIndex + 1) % listSize]
                 )
            }

            variantsToShow.forEach { variant ->
                val actionIntent = Intent(context, ActionReceiver::class.java).apply {
                    action = "ACTION_SELECT_VARIANT"
                    putExtra("VARIANT_ID", variant.id)
                }
                val actionPendingIntent = PendingIntent.getBroadcast(
                    context, variant.id.hashCode() + startIndex, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, variant.name, actionPendingIntent)
            }

            if (listSize > 3) {
                val nextIntent = Intent(context, ActionReceiver::class.java).apply {
                    action = "ACTION_NEXT_VARIANTS"
                    putExtra("CURRENT_INDEX", actualStartIndex)
                }
                val nextPendingIntent = PendingIntent.getBroadcast(
                    context, 999, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Dalej...", nextPendingIntent)
            }
        }

        notificationManager.notify(REMINDER_ID, builder.build())
    }

    fun showAlarmNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALARM_ID, notification)
    }
}
