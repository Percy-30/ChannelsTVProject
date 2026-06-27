package com.streamingtv.channelstv.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repository that reads channels and events from Firebase Realtime Database.
 *
 * Firebase structure expected:
 * /channels/{id}: Channel object
 * /events/{id}: Event object
 */
class FirebaseRepository {

    private val database = FirebaseDatabase.getInstance()

    /**
     * Returns a Flow of all channels, sorted by [Channel.order].
     */
    fun getChannels(): Flow<List<Channel>> = callbackFlow {
        val ref = database.getReference("channels")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val channels = snapshot.children.mapNotNull { child ->
                    try {
                        val id = child.key ?: ""
                        var scrapeUrl = child.child("scrapeUrl").getValue(String::class.java)

                        // Fallback local para asegurar 100% de funcionalidad en canales caídos
                        val localScrapeOverrides = mapOf(
                            "-OuQ6PTPvjpxMhnzpc4U" to "https://futemax.boston/espn-ao-vivo-assista-esportes-online-em-hd/",
                            "-OuQBNfEl7Zyy3dq7NBC" to "https://futemax.boston/espn-2-ao-vivo-assista-esportes-em-hd/",
                            "-OuQBYI4MN5BJAjsIPzL" to "https://futemax.boston/espn-3-ao-vivo-assista-esportes-em-hd/",
                            "-OuQBi3g9IXv_VbkOQGq" to "https://futemax.boston/espn-4-ao-vivo-assista-esportes-em-hd/",
                            "-OvGdVmr2seoMMJtQ6Ns" to "https://futemax.boston/espn-ao-vivo-assista-esportes-online-em-hd/",
                            "-OvGdmsaeEPGr0srQAJi" to "https://futemax.boston/espn-2-ao-vivo-assista-esportes-em-hd/",
                            "-OvGe2MhaW19igxgqnsf" to "https://futemax.boston/espn-3-ao-vivo-assista-esportes-em-hd/",
                            "-OuQ8MXHIyUZ494Da-9A" to "https://futemax.boston/sportv-ao-vivo-assista-esportes-online-em-hd/"
                        )

                        val localUrlOverrides = mapOf(
                            "-Ouvyejgz1SBVnBgmNDY" to "http://190.11.225.124:5000/live/universo_hd/playlist.m3u8", // Universo
                            "-OuxjzeDHFEwpUGYVwdh" to "https://content.uplynk.com/channel/b6a96ed39d694ae1b738faa98cf7dd3f.m3u8", // Telemundo
                            "-Ouw0uWdFCYhKpqPsmvT" to "http://138.121.15.230:9002/TVN/index.m3u8" // TVN Chile
                        )

                        if (localScrapeOverrides.containsKey(id)) {
                            scrapeUrl = localScrapeOverrides[id]
                        }

                        var finalUrl = child.child("url").getValue(String::class.java) ?: ""
                        if (localUrlOverrides.containsKey(id)) {
                            finalUrl = localUrlOverrides[id]!!
                            scrapeUrl = null // Si tenemos URL directa, no usamos scraper
                        }

                        Channel(
                            id = id,
                            name = child.child("name").getValue(String::class.java) ?: "",
                            url = finalUrl,
                            logoUrl = child.child("logoUrl").getValue(String::class.java),
                            drmKeyId = child.child("drmKeyId").getValue(String::class.java),
                            drmKey = child.child("drmKey").getValue(String::class.java),
                            drmKeysJson = child.child("drmKeysJson").getValue(String::class.java),
                            category = child.child("category").getValue(String::class.java) ?: "Variados",
                            type = child.child("type").getValue(String::class.java) ?: "video",
                            order = child.child("order").getValue(Int::class.java) ?: 0,
                            scrapeUrl = scrapeUrl
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.order }
                trySend(channels)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Returns a Flow of all scheduled events, sorted by [Event.timestamp].
     */
    fun getEvents(): Flow<List<Event>> = callbackFlow {
        val ref = database.getReference("events")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val events = snapshot.children.mapNotNull { child ->
                    try {
                        Event(
                            id = child.key ?: "",
                            title = child.child("title").getValue(String::class.java) ?: "",
                            subtitle = child.child("subtitle").getValue(String::class.java) ?: "",
                            timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                            logoUrl = child.child("logoUrl").getValue(String::class.java),
                            channelId = child.child("channelId").getValue(String::class.java)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.timestamp }
                trySend(events)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }
}
