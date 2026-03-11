package com.taxiapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.taxiapp.data.model.User
import com.taxiapp.util.Resource
import kotlinx.coroutines.channels.awaitClose
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
                if (user != null) {
                    trySend(Resource.Success(user))
                } else {
                    trySend(Resource.Error("User data not found"))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Resource.Error(error.message))
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }


    suspend fun getRecentDestinations(): Resource<List<RecentDestination>> {
        return try {
            if (uid.isEmpty()) return Resource.Error("Not authenticated")

            val snapshot = database
                .getReference("users/$uid/recentTrips")
                .orderByChild("timestamp")
                .limitToLast(5)
                .get()
                .await()

            val list = snapshot.children
                .mapNotNull { it.getValue(RecentDestination::class.java) }
                .sortedByDescending { it.timestamp }

            Resource.Success(list)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load recent trips")
        }
    }
}


data class RecentDestination(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = 0L
)