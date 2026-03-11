package com.taxiapp.ui.auth

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.taxiapp.data.repository.AuthRepository
import com.taxiapp.util.Resource
import com.taxiapp.util.mapSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val repository: AuthRepository
) : AndroidViewModel(application) {

    val isLoggedIn: Boolean get() = repository.isLoggedIn


    private val _loginState = MutableStateFlow<Resource<Unit>?>(null)
    val loginState: StateFlow<Resource<Unit>?> = _loginState.asStateFlow()

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = Resource.Loading
            _loginState.value = repository.loginWithEmail(email, password).mapSuccess { Unit }
        }
    }


    private val _signUpState = MutableStateFlow<Resource<Unit>?>(null)
    val signUpState: StateFlow<Resource<Unit>?> = _signUpState.asStateFlow()

    fun signUpWithEmail(name: String, email: String, phone: String, password: String) {
        viewModelScope.launch {
            _signUpState.value = Resource.Loading
            _signUpState.value = repository
                .signUpWithEmail(name, email, phone, password)
                .mapSuccess { Unit }
        }
    }


    private val _forgotPasswordState = MutableStateFlow<Resource<Unit>?>(null)
    val forgotPasswordState: StateFlow<Resource<Unit>?> = _forgotPasswordState.asStateFlow()

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _forgotPasswordState.value = Resource.Loading
            _forgotPasswordState.value = repository.sendPasswordReset(email)
        }
    }


    private val _otpSendState = MutableStateFlow<Resource<String>?>(null)
    val otpSendState: StateFlow<Resource<String>?> = _otpSendState.asStateFlow()


    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun sendOtp(phoneNumber: String, activity: FragmentActivity) {
        _otpSendState.value = Resource.Loading
        repository.sendOtp(
            phoneNumber = phoneNumber,
            activity = activity,
            callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {


                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    viewModelScope.launch {
                        _otpVerifyState.value = Resource.Loading
                        _otpVerifyState.value = repository
                            .signInWithCredential(credential)
                            .mapSuccess { Unit }
                    }
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    _otpSendState.value = Resource.Error(e.message ?: "Failed to send OTP")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    resendToken = token
                    _otpSendState.value = Resource.Success(verificationId)
                }
            }
        )
    }


    private val _otpVerifyState = MutableStateFlow<Resource<Unit>?>(null)
    val otpVerifyState: StateFlow<Resource<Unit>?> = _otpVerifyState.asStateFlow()


    fun verifyOtp(verificationId: String, smsCode: String) {
        viewModelScope.launch {
            _otpVerifyState.value = Resource.Loading
            _otpVerifyState.value = repository
                .verifyOtp(verificationId, smsCode)
                .mapSuccess { Unit }
        }
    }


    private val _userRoleState = MutableStateFlow<Resource<String>?>(null)
    val userRoleState: StateFlow<Resource<String>?> = _userRoleState.asStateFlow()

    fun fetchUserRole() {
        viewModelScope.launch {
            _userRoleState.value = Resource.Loading
            _userRoleState.value = repository.fetchOrCreateUserRole()
        }
    }


    fun resetLoginState() { _loginState.value = null }
    fun resetSignUpState() { _signUpState.value = null }
    fun resetForgotPassState() { _forgotPasswordState.value = null }
    fun resetOtpVerifyState() { _otpVerifyState.value = null }
    fun resetRoleState() { _userRoleState.value = null }
}
