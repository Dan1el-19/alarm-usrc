package dev.usrc.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MorningAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Log the event or perform other internal tasks if needed
        val alarmTime = intent.getStringExtra("ALARM_TIME") ?: "Alarm"
        Logger.d("MorningAlarmReceiver: Alarm triggered for $alarmTime")
        
        // Notification removed as per user request
    }
}
