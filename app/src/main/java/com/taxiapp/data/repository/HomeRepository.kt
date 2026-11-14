package com.taxiapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.User
import com.taxiapp.util.Resource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) {

    private val uid: String get() = auth.currentUser?.uid ?: ""

    fun observeUser(): Flow<Resource<User>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(Resource.Error("User not authenticated"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val ref = database.getReference("users/$uid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) trySend(Resource.Success(user))
                else trySend(Resource.Error("User data not found"))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Resource.Error(error.message))
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * The rider's most recent trips, newest first. Uses the users/{uid}/rides index
     * written when a trip is created; trip ids are push keys, so they sort by time.
     */
    suspend fun getRideHistory(limit: Int = 30): Resource<List<Trip>> {
        if (uid.isEmpty()) return Resource.Error("Not authenticated")
        return try {
            val ids = database.getReference("users/$uid/rides")
                .orderByKey()
                .limitToLast(limit)
                .get()
                .await()
                .children
                .mapNotNull { it.key }
                .reversed()

            val trips = coroutineScope {
                ids.map { id ->
                    async {
                        runCatching {
                            database.getReference("trips/$id").get().await().getValue(Trip::class.java)
                        }.getOrNull()
                    }
                }.awaitAll()
            }.filterNotNull()

            Resource.Success(trips)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load rides")
        }
    }
}

/** A place the rider went before, offered as a one-tap destination. */
data class RecentDestination(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = 0L
)
