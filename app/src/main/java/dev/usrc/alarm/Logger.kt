package dev.usrc.alarm

import android.util.Log
import androidx.compose.runtime.mutableStateListOf

object Logger {
    private const val TAG = "AlarmApp"
    val logs = mutableStateListOf<String>()

    fun d(message: String) {
        Log.d(TAG, message)
        addLog("DEBUG: $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        addLog("ERROR: $message ${throwable?.message ?: ""}")
    }

    private fun addLog(text: String) {
        if (logs.size > 200) logs.removeAt(0)
        logs.add("${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))} $text")
    }
}
