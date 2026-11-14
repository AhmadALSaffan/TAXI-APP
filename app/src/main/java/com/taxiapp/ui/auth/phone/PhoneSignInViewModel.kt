package com.taxiapp.ui.auth.phone

import android.app.Activity
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.taxiapp.R
import com.taxiapp.data.repository.AuthRepository
import com.taxiapp.data.repository.FcmTokenSync
import com.taxiapp.ui.auth.Country
import com.taxiapp.ui.auth.CountryUtils
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class PhoneStep { Number, Code }

data class PhoneSignInState(
    val step: PhoneStep = PhoneStep.Number,
    val country: Country,
    val digits: String = "",
    val code: String = "",
    val sending: Boolean = false,
    val verifying: Boolean = false,
    val resendSeconds: Int = 0,
    val numberError: String? = null,
    val codeError: String? = null,
    /** One-shot message for the snackbar; cleared with [PhoneSignInViewModel.onMessageShown]. */
    val message: String? = null,
    /** Set once signed in; the screen navigates away. */
    val signedInRole: String? = null,
) {
    val e164: String get() = country.dialCode + digits.trimStart('0')
    val canContinue: Boolean get() = digits.length in 6..15 && !sending
}

@HiltViewModel
class PhoneSignInViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val fcmTokenSync: FcmTokenSync,
) : ViewModel() {

    val countries: List<Country> = CountryUtils.getCountries(context)

    private val _state = MutableStateFlow(PhoneSignInState(country = detectCountry()))
    val state: StateFlow<PhoneSignInState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countdown: Job? = null

    // ─── Number step ───────────────────────────────────────────────────────

    fun onDigitsChange(value: String) {
        _state.update { it.copy(digits = value.filter(Char::isDigit).take(15), numberError = null) }
    }

    fun onCountrySelected(country: Country) {
        _state.update { it.copy(country = country, numberError = null) }
    }

    fun sendCode(activity: Activity) {
        val s = _state.value
        if (!s.canContinue) {
            _state.update { it.copy(numberError = context.getString(R.string.phone_error_invalid)) }
            return
        }
        _state.update { it.copy(sending = true, numberError = null) }
        authRepository.sendOtp(s.e164, activity, callbacks(isResend = false))
    }

    // ─── Code step ─────────────────────────────────────────────────────────

    fun onCodeChange(value: String) {
        if (_state.value.verifying) return
        val code = value.filter(Char::isDigit).take(CODE_LENGTH)
        _state.update { it.copy(code = code, codeError = null) }
        if (code.length == CODE_LENGTH) verify(code)
    }

    fun resend(activity: Activity) {
        val s = _state.value
        if (s.resendSeconds > 0 || s.sending) return
        _state.update { it.copy(sending = true) }
        authRepository.sendOtp(s.e164, activity, callbacks(isResend = true), resendToken)
    }

    fun editNumber() {
        countdown?.cancel()
        _state.update { it.copy(step = PhoneStep.Number, code = "", codeError = null, resendSeconds = 0) }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun verify(code: String) {
        val id = verificationId
        if (id == null) {
            _state.update { it.copy(code = "", message = context.getString(R.string.phone_error_session)) }
            return
        }
        _state.update { it.copy(verifying = true) }
        viewModelScope.launch {
            when (authRepository.verifyOtp(id, code)) {
                is Resource.Success -> finishSignIn()
                else -> _state.update {
                    it.copy(verifying = false, code = "", codeError = context.getString(R.string.phone_error_wrong_code))
                }
            }
        }
    }

    private fun signInWith(credential: PhoneAuthCredential) {
        _state.update { it.copy(verifying = true, code = credential.smsCode ?: it.code) }
        viewModelScope.launch {
            when (val result = authRepository.signInWithCredential(credential)) {
                is Resource.Success -> finishSignIn()
                is Resource.Error -> _state.update { it.copy(verifying = false, message = result.message) }
                else -> Unit
            }
        }
    }

    private suspend fun finishSignIn() {
        val role = (authRepository.fetchOrCreateUserRole() as? Resource.Success)?.data ?: "passenger"
        fcmTokenSync.syncForCurrentUser()
        countdown?.cancel()
        _state.update { it.copy(verifying = false, signedInRole = role) }
    }

    private fun callbacks(isResend: Boolean) = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            _state.update {
                it.copy(
                    step = PhoneStep.Code,
                    sending = false,
                    code = "",
                    codeError = null,
                    message = if (isResend) context.getString(R.string.phone_code_resent) else null,
                )
            }
            startCountdown()
        }

        // Instant verification or SMS auto-retrieval: no typing needed.
        override fun onVerificationCompleted(credential: PhoneAuthCredential) = signInWith(credential)

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w(TAG, "Phone verification failed", e)
            _state.update {
                when (e) {
                    is FirebaseAuthInvalidCredentialsException -> it.copy(
                        sending = false, verifying = false,
                        numberError = context.getString(R.string.phone_error_invalid),
                    )
                    is FirebaseTooManyRequestsException -> it.copy(
                        sending = false, verifying = false,
                        message = context.getString(R.string.phone_error_too_many),
                    )
                    else -> it.copy(
                        sending = false, verifying = false,
                        message = context.getString(R.string.phone_error_send),
                    )
                }
            }
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (s in RESEND_AFTER_SECONDS downTo 0) {
                _state.update { it.copy(resendSeconds = s) }
                if (s > 0) delay(1_000)
            }
        }
    }

    /** SIM country, then network, then the device locale; United States if none match. */
    private fun detectCountry(): Country {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val iso = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { it.length == 2 }
        val flag = iso?.let(CountryUtils::flagFor)
        return countries.firstOrNull { it.flag == flag }
            ?: countries.firstOrNull { it.dialCode == "+1" }
            ?: Country("🇺🇸", "United States", "+1")
    }

    private companion object {
        const val TAG = "PhoneSignIn"
        const val CODE_LENGTH = 6
        const val RESEND_AFTER_SECONDS = 60
    }
}
