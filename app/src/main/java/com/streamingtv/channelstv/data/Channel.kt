package com.streamingtv.channelstv.data

/**
 * Data class representing a live TV channel.
 * Recovered from APK decompilation (Channel.kt original source).
 */
data class Channel(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val logoUrl: String? = null,
    val drmKeyId: String? = null,
    val drmKey: String? = null,
    val drmKeysJson: String? = null,
    val category: String = "Variados",
    val type: String = "video",
    val order: Int = 0,
    /** Si está presente, la URL del stream se extrae dinámicamente (WebView scraper). */
    val scrapeUrl: String? = null
)

/** Retorna true si este canal requiere extracción dinámica de stream. */
val Channel.isScrapeable: Boolean get() = !scrapeUrl.isNullOrBlank()
