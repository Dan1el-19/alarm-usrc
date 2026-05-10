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

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val TAG = "AlarmScheduler"
        const val REMINDER_REQUEST_CODE = 1000
        const val SECOND_REMINDER_REQUEST_CODE = 1001
        const val FALLBACK_REQUEST_CODE = 1002
        const val CLEAR_STATE_REQUEST_CODE = 1003
        const val ALARM_BASE_REQUEST_CODE = 2000
    }

    fun scheduleReminder(timeString: String) {
        val time = LocalTime.parse(timeString)
        var scheduledTime = ZonedDateTime.of(LocalDate.now(), time, ZoneId.systemDefault())

        if (scheduledTime.isBefore(ZonedDateTime.now())) {
            scheduledTime = scheduledTime.plusDays(1)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "ACTION_FIRST_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REMINDER_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledTime.toInstant().toEpochMilli(),
                pendingIntent
            )
        }
        
        // Also cancel any pending second or fallback alarms if we are rescheduling the main cycle
        cancelSecondReminder()
        cancelFallback()
    }

    fun scheduleSecondReminder() {
        val triggerTime = System.currentTimeMillis() + 3600_000 // 1 hour later
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "ACTION_SECOND_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, SECOND_REMINDER_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun scheduleFallback() {
        val triggerTime = System.currentTimeMillis() + 7200_000 // 2 hours later total
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "ACTION_FALLBACK_ALARM"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, FALLBACK_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelSecondReminder() {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, SECOND_REMINDER_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun cancelFallback() {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, FALLBACK_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleAlarmsForVariant(variant: AlarmVariant) {
        // We now route through scheduleVariantForDate for lifecycle management
        val tomorrow = LocalDate.now().plusDays(1).toString()
        scheduleVariantForDate(variant, tomorrow, "manual")
    }

    fun scheduleVariantForDate(variant: AlarmVariant, date: String, source: String) {
        Logger.d("LIFECYCLE: Scheduling variant ${variant.name} for $date (Source: $source)")
        
        // 1. Save state
        val repository = SettingsRepository(context)
        repository.updateSettings { 
            it.copy(
                selectedVariantId = variant.id,
                selectedVariantDate = date,
                selectedVariantSource = source,
                lastSelectedVariantId = variant.id,
                lastSelectedDate = LocalDate.now().toString()
            )
        }

        // 2. Cancel old lifecycle alarms
        cancelClearSelectedVariant()

        // 3. Delegate to synchronizer for system and internal backup alarms
        val synchronizer = AlarmSynchronizer(context)
        synchronizer.synchronize(variant)

        // 4. Schedule state clearing
        val clearTime = getLastAlarmDateTime(variant, date).plusMinutes(1)
        scheduleClearSelectedVariant(clearTime)
        
        repository.updateSettings { it.copy(clearStateScheduledAt = clearTime.toString()) }
    }

    fun scheduleClearSelectedVariant(dateTime: ZonedDateTime) {
        Logger.d("LIFECYCLE: Scheduling state clear at $dateTime")
        val intent = Intent(context, ClearSelectedVariantReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, CLEAR_STATE_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dateTime.toInstant().toEpochMilli(),
                pendingIntent
            )
        }
    }

    fun cancelClearSelectedVariant() {
        Logger.d("LIFECYCLE: Canceling previous clear state alarm")
        val intent = Intent(context, ClearSelectedVariantReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, CLEAR_STATE_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun getLastAlarmDateTime(variant: AlarmVariant, date: String): ZonedDateTime {
        val targetDate = LocalDate.parse(date)
        if (variant.noAlarms || variant.alarmTimes.isEmpty()) {
            // For "Wolne" (no alarms), clear state at the end of the day or before next cycle
            // Using 23:59 as a stable point for clearing the state of a "Wolne" day.
            return ZonedDateTime.of(targetDate, LocalTime.of(23, 59), ZoneId.systemDefault())
        }
        
        val lastTime = variant.alarmTimes.map { LocalTime.parse(it) }.maxOrNull() ?: LocalTime.of(8, 0)
        return ZonedDateTime.of(targetDate, lastTime, ZoneId.systemDefault())
    }

    fun rescheduleActiveSelectionAfterBoot() {
        val repository = SettingsRepository(context)
        val settings = repository.getSettings()
        
        val selectedId = settings.selectedVariantId
        val selectedDateStr = settings.selectedVariantDate
        
        if (selectedId != null && selectedDateStr != null) {
            val selectedDate = LocalDate.parse(selectedDateStr)
            val now = LocalDate.now()
            
            if (!selectedDate.isBefore(now)) {
                Logger.d("BOOT: Restoring active selection for $selectedDateStr")
                val variant = settings.variants.find { it.id == selectedId }
                if (variant != null) {
                    // Re-schedule internal backup alarms and clear-state
                    // Note: We don't re-trigger synchronizer as system alarms persist usually 
                    // and we don't want to re-open Clock app if it happened.
                    // But we MUST restore our internal state-clearing alarm and backup alarms.
                    val clearTime = getLastAlarmDateTime(variant, selectedDateStr).plusMinutes(1)
                    if (clearTime.isAfter(ZonedDateTime.now())) {
                        scheduleClearSelectedVariant(clearTime)
                        
                        // Also restore internal backup alarms if necessary
                        val synchronizer = AlarmSynchronizer(context)
                        synchronizer.restoreInternalAlarmsOnly(variant, selectedDateStr)
                    }
                }
            } else {
                Logger.d("BOOT: Active selection $selectedDateStr is in the past, clearing.")
                val repository = SettingsRepository(context)
                repository.updateSettings { 
                    it.copy(
                        selectedVariantId = null,
                        selectedVariantDate = null,
                        selectedVariantSource = null
                    )
                }
            }
        }
    }

    fun cancelAllAlarms() {
        Logger.d("Canceling all internal alarms")
        // Cancel internal alarms
        for (i in 0 until 10) {
            val intent = Intent(context, MorningAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_BASE_REQUEST_CODE + i, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        // Removing system dismiss loop to prevent switching to Clock app
    }

    fun cancelReminder() {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REMINDER_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleTestReminder() {
        val triggerTime = System.currentTimeMillis() + 10_000
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REMINDER_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
}
