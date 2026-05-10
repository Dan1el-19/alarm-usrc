package dev.usrc.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = SettingsRepository(context)
            val settings = repository.getSettings()
            
            if (settings.reminderEnabled) {
                val scheduler = AlarmScheduler(context)
                scheduler.scheduleReminder(settings.reminderTime)
            }
            
            // Restore active selection lifecycle (clear-state and backup alarms)
            val scheduler = AlarmScheduler(context)
            scheduler.rescheduleActiveSelectionAfterBoot()
        }
    }
}
