package dev.usrc.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                handleReceive(context, intent)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun handleReceive(context: Context, intent: Intent) {
        val repository = SettingsRepository(context)
        val settings = repository.getSettings()
        val scheduler = AlarmScheduler(context)
        val notificationHelper = NotificationHelper(context)
        
        val today = LocalDate.now().toString()
        val alreadySelected = settings.lastSelectedDate == today

        when (intent.action) {
            "ACTION_FIRST_REMINDER" -> {
                if (!alreadySelected) {
                    showReminder(notificationHelper, settings)
                    scheduler.scheduleSecondReminder()
                }
                // Always schedule main reminder for tomorrow
                scheduler.scheduleReminder(settings.reminderTime)
            }
            "ACTION_SECOND_REMINDER" -> {
                if (!alreadySelected) {
                    showReminder(notificationHelper, settings, isUrgent = true)
                    scheduler.scheduleFallback()
                }
            }
            "ACTION_FALLBACK_ALARM" -> {
                if (!alreadySelected) {
                    applyFallback(scheduler, notificationHelper, settings)
                }
            }
            else -> {
                // Default legacy behavior or manual trigger
                showReminder(notificationHelper, settings)
            }
        }
    }

    private fun showReminder(helper: NotificationHelper, settings: AppSettings, isUrgent: Boolean = false) {
        val title = if (isUrgent) "PILNE: ${settings.notificationTitle}" else settings.notificationTitle
        if (settings.variants.isNotEmpty()) {
            helper.showReminderNotificationWithActions(title, settings.notificationMessage, settings.variants)
        } else {
            helper.showReminderNotification(title, settings.notificationMessage)
        }
    }

    private fun applyFallback(
        scheduler: AlarmScheduler,
        helper: NotificationHelper,
        settings: AppSettings,
    ) {
        val tomorrow = LocalDate.now().plusDays(1)
        val dayOfWeek = tomorrow.dayOfWeek.value // Tomorrow's day
        val fallbackVariantId = settings.dayDefaults[dayOfWeek]
        val variant = settings.variants.find { it.id == fallbackVariantId } 
            ?: settings.variants.find { it.isDefault }
            ?: settings.variants.firstOrNull()

        if (variant != null) {
            Logger.d("LIFECYCLE: Falling back to weekly schedule variant: ${variant.name}")
            scheduler.scheduleVariantForDate(variant, tomorrow.toString(), "fallback")

            helper.showReminderNotification(
                "Automatyczny wybór",
                "Nie wybrano wariantu. Ustawiono domyślny: ${variant.name}"
            )
        }
    }
}
