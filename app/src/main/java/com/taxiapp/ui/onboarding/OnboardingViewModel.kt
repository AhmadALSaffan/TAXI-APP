package com.taxiapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taxiapp.data.repository.AuthRepository
import com.taxiapp.data.repository.FcmTokenSync
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SignInState {
    data object Idle : SignInState
    data object Loading : SignInState
    data class Error(val message: String) : SignInState
    data class SignedIn(val role: String) : SignInState
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val fcmTokenSync: FcmTokenSync,
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    fun onGoogleRequested() {
        _state.value = SignInState.Loading
    }

    fun onGoogleResult(result: GoogleIdTokenResult) {
        when (result) {
            GoogleIdTokenResult.Cancelled -> _state.value = SignInState.Idle
            is GoogleIdTokenResult.Failure -> _state.value = SignInState.Error(result.message)
            is GoogleIdTokenResult.Success -> signIn(result.idToken)
        }
    }

    fun onErrorShown() {
        if (_state.value is SignInState.Error) _state.value = SignInState.Idle
    }

    private fun signIn(idToken: String) {
        viewModelScope.launch {
            _state.value = SignInState.Loading
            when (val auth = authRepository.signInWithGoogle(idToken)) {
                is Resource.Error -> _state.value = SignInState.Error(auth.message)
                is Resource.Success -> {
                    _state.value = when (val role = authRepository.fetchOrCreateUserRole()) {
                        is Resource.Success -> {
                            fcmTokenSync.syncForCurrentUser()
                            SignInState.SignedIn(role.data)
                        }
                        is Resource.Error -> SignInState.Error(role.message)
                        else -> SignInState.Error("Something went wrong. Try again.")
                    }
                }
                else -> Unit
            }
        }
    }
}
