package com.streamingtv.channelstv.data

/**
 * Data class representing a scheduled broadcast event (e.g., a match or show).
 * Recovered from APK decompilation (Event.kt original source).
 */
data class Event(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val timestamp: Long = 0L,
    val logoUrl: String? = null,
    val channelId: String? = null
)
