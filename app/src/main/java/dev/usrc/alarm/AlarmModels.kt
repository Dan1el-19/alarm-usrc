package dev.usrc.alarm

import kotlinx.serialization.Serializable

@Serializable
data class AlarmVariant(
    val id: String,
    val name: String,
    val alarmTimes: List<String>,
    val noAlarms: Boolean = false,
    val isDefault: Boolean = false
)

@Serializable
data class AppSettings(
    val reminderTime: String = "20:00",
    val notificationTitle: String = "Alarm",
    val notificationMessage: String = "Wybierz wariant na jutro",
    val variants: List<AlarmVariant> = listOf(
        AlarmVariant("1", "8:00", listOf("06:20", "06:30", "06:40"), isDefault = true),
        AlarmVariant("2", "8:55", listOf("07:20", "07:30", "07:40")),
        AlarmVariant("3", "9:50", listOf("08:20", "08:30", "08:40")),
        AlarmVariant("4", "10:45", listOf("09:20", "09:30", "09:40")),
        AlarmVariant("5", "11:50", listOf("10:20", "10:30", "10:40")),
        AlarmVariant("6", "Wolne", emptyList(), noAlarms = true)
    ),
    val reminderEnabled: Boolean = false,
    val lastSelectedVariantId: String? = null,
    val lastSelectedDate: String? = null, // The date when the user MADE the selection
    val dayDefaults: Map<Int, String> = emptyMap(), // DayOfWeek (1-7) to VariantId
    val selectedVariantId: String? = null,
    val selectedVariantDate: String? = null, // The date the alarms are FOR (tomorrow)
    val selectedVariantSource: String? = null, // "manual" | "fallback"
    val clearStateScheduledAt: String? = null,
    val retryCount: Int = 0
)
