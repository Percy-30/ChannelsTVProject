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
                        Channel(
                            id = child.key ?: "",
                            name = child.child("name").getValue(String::class.java) ?: "",
                            url = child.child("url").getValue(String::class.java) ?: "",
                            logoUrl = child.child("logoUrl").getValue(String::class.java),
                            drmKeyId = child.child("drmKeyId").getValue(String::class.java),
                            drmKey = child.child("drmKey").getValue(String::class.java),
                            drmKeysJson = child.child("drmKeysJson").getValue(String::class.java),
                            category = child.child("category").getValue(String::class.java) ?: "Variados",
                            type = child.child("type").getValue(String::class.java) ?: "video",
                            order = child.child("order").getValue(Int::class.java) ?: 0
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
