package dev.usrc.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmSynchronizer(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun synchronize(variant: AlarmVariant) {
        Logger.d("SYNC: Starting standalone sync for ${variant.name}")
        try {
            // 1. Internal State Verification/Cleanup (Only our app's alarms)
            cancelInternalAlarms()

            if (variant.noAlarms) {
                Logger.d("SYNC: Variant is 'No Alarms'. Internal cleanup done.")
                return
            }

            val tomorrow = LocalDate.now().plusDays(1).toString()
            executeSchedule(variant, tomorrow)

            Logger.d("SYNC: Completed successfully for ${variant.name}")
        } catch (e: Exception) {
            Logger.e("SYNC: Critical error during synchronization", e)
        }
    }

    fun restoreInternalAlarmsOnly(variant: AlarmVariant, date: String) {
        Logger.d("SYNC: Restoring internal alarms only for $date")
        cancelInternalAlarms()
        if (variant.noAlarms) return

        variant.alarmTimes.forEachIndexed { index, timeString ->
            scheduleInternalAlarmOnly(index, timeString, date)
        }
    }

    private fun executeSchedule(variant: AlarmVariant, date: String) {
        variant.alarmTimes.forEachIndexed { index, timeString ->
            val success = scheduleSingleAlarm(index, timeString, date)
            if (!success) {
                Logger.e("SYNC: Failed to schedule alarm $index ($timeString). Retrying...")
                Thread.sleep(1000)
                scheduleSingleAlarm(index, timeString, date)
            }
            Thread.sleep(1000)
        }
    }

    private fun scheduleSingleAlarm(index: Int, timeString: String, date: String): Boolean {
        return try {
            val time = LocalTime.parse(timeString)
            // A. Internal Fallback Alarm
            scheduleInternalAlarmOnly(index, timeString, date)

            // B. System Clock App Sync (Samsung etc.)
            val systemAlarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(systemAlarmIntent)
            
            Logger.d("SYNC: Sent intent for $timeString")
            true
        } catch (e: Exception) {
            Logger.e("SYNC: Error in scheduleSingleAlarm for $timeString", e)
            false
        }
    }

    private fun scheduleInternalAlarmOnly(index: Int, timeString: String, date: String) {
        val time = LocalTime.parse(timeString)
        val scheduledTime = ZonedDateTime.of(LocalDate.parse(date), time, ZoneId.systemDefault())
        
        val intent = Intent(context, MorningAlarmReceiver::class.java).apply {
            putExtra("ALARM_TIME", timeString)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, AlarmScheduler.ALARM_BASE_REQUEST_CODE + index, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context, AlarmScheduler.ALARM_BASE_REQUEST_CODE + index, showIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(scheduledTime.toInstant().toEpochMilli(), showPendingIntent)
        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            Logger.e("SYNC: SecurityException while setting alarm clock", e)
        }
        Logger.d("SYNC: Internal backup alarm $index set for $timeString")
    }

    private fun cancelInternalAlarms() {
        for (i in 0 until 10) {
            val intent = Intent(context, MorningAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, AlarmScheduler.ALARM_BASE_REQUEST_CODE + i, intent, 
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
