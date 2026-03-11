package com.taxiapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.taxiapp.data.model.User
import com.taxiapp.util.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) {

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = auth.currentUser != null


    suspend fun signUpWithEmail(
        name: String,
        email: String,
        phone: String,
        password: String
    ): Resource<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val fbUser  = result.user!!

            val user = User(
                uid         = fbUser.uid,
                displayName = name,
                email       = email,
                phone       = phone,
                rating      = 5.0,
                memberSince = System.currentTimeMillis(),
                role        = "passenger"             // ← default role on email sign-up
            )
            database.getReference("users/${fbUser.uid}").setValue(user).await()

            Resource.Success(fbUser)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sign-up failed")
        }
    }


    suspend fun loginWithEmail(email: String, password: String): Resource<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Resource.Success(result.user!!)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Login failed")
        }
    }


    suspend fun sendPasswordReset(email: String): Resource<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send reset email")
        }
    }


    fun sendOtp(
        phoneNumber: String,
        activity: androidx.fragment.app.FragmentActivity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }


    suspend fun verifyOtp(verificationId: String, smsCode: String): Resource<FirebaseUser> {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
            signInWithCredential(credential)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "OTP verification failed")
        }
    }

    suspend fun signInWithCredential(credential: PhoneAuthCredential): Resource<FirebaseUser> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            Resource.Success(result.user!!)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sign-in failed")
        }
    }


    suspend fun fetchOrCreateUserRole(): Resource<String> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Resource.Error("Not authenticated")

            val ref      = database.getReference("users/$uid")
            val snapshot = ref.get().await()

            if (snapshot.exists()) {
                // ── Existing user: read role ──────────────────────────────────
                val role = snapshot.child("role").getValue(String::class.java)
                    ?: "passenger"                           // field missing → default passenger
                Resource.Success(role)

            } else {
                val firebaseUser = auth.currentUser!!
                val newUser = User(
                    uid         = uid,
                    phone       = firebaseUser.phoneNumber ?: "",
                    displayName = "",
                    email       = firebaseUser.email ?: "",
                    photoUrl    = firebaseUser.photoUrl?.toString() ?: "",
                    rating      = 5.0,
                    memberSince = System.currentTimeMillis(),
                    role        = "passenger"
                )
                ref.setValue(newUser).await()
                Resource.Success("passenger")
            }

        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch user role")
        }
    }


    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun signOut() = auth.signOut()
}