package dev.usrc.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d("ActionReceiver: Received action ${intent.action}")
        val repository = SettingsRepository(context)
        val settings = repository.getSettings()
        val action = intent.action

        when {
            action == "ACTION_SELECT_VARIANT" -> {
                val variantId = intent.getStringExtra("VARIANT_ID")
                Logger.d("ActionReceiver: Selecting variant ID: $variantId")
                val variant = settings.variants.find { it.id == variantId }
                if (variant != null) {
                    val scheduler = AlarmScheduler(context)
                    scheduler.scheduleAlarmsForVariant(variant)
                    scheduler.cancelSecondReminder()
                    scheduler.cancelFallback()
                    
                    repository.updateSettings { 
                        it.copy(
                            lastSelectedVariantId = variant.id,
                            lastSelectedDate = LocalDate.now().toString()
                        )
                    }
                    
                    val notificationHelper = NotificationHelper(context)
                    notificationHelper.showReminderNotification(
                        "Alarm ustawiony",
                        "Wariant: ${variant.name} na jutro."
                    )
                } else {
                    Logger.e("Variant not found for ID: $variantId")
                }
            }
            action == "ACTION_NEXT_VARIANTS" -> {
                val currentIndex = intent.getIntExtra("CURRENT_INDEX", 0)
                Logger.d("ActionReceiver: Next variants from index: $currentIndex")
                val notificationHelper = NotificationHelper(context)
                notificationHelper.showReminderNotificationWithActions(
                    settings.notificationTitle,
                    settings.notificationMessage,
                    settings.variants,
                    currentIndex + 2
                )
            }
        }
    }
}
