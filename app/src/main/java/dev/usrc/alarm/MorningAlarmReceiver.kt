package dev.usrc.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MorningAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmTime = intent.getStringExtra("ALARM_TIME") ?: "Alarm"
        
        val notificationHelper = NotificationHelper(context)
        notificationHelper.showAlarmNotification(
            "Alarm: $alarmTime",
            "Wstań i zwyciężaj!"
        )
    }
}
