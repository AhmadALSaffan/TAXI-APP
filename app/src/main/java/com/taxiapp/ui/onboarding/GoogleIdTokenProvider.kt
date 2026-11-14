package com.taxiapp.ui.onboarding

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.taxiapp.R

sealed interface GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult
    /** The user closed the Google sheet. Not an error; show nothing. */
    data object Cancelled : GoogleIdTokenResult
    data class Failure(val message: String) : GoogleIdTokenResult
}

/** Shows the "Sign in with Google" sheet and returns the account's ID token. */
class GoogleIdTokenProvider(private val activity: Activity) {

    private val credentialManager = CredentialManager.create(activity)

    suspend fun request(): GoogleIdTokenResult {
        // Generated from google-services.json by the Google services plugin: the
        // web client that Firebase's Google sign-in provider accepts tokens for.
        val webClientId = activity.getString(R.string.default_web_client_id)

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()

        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                GoogleIdTokenResult.Failure(activity.getString(R.string.onboarding_error_google_generic))
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleIdTokenResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleIdTokenResult.Failure(activity.getString(R.string.onboarding_error_no_account))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "getCredential failed", e)
            GoogleIdTokenResult.Failure(activity.getString(R.string.onboarding_error_google_generic))
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Invalid Google ID token", e)
            GoogleIdTokenResult.Failure(activity.getString(R.string.onboarding_error_google_generic))
        }
    }

    private companion object {
        const val TAG = "GoogleIdTokenProvider"
    }
}
