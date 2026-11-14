package com.taxiapp.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.taxiapp.ui.auth.PhoneEntryActivity
import com.taxiapp.ui.driver.DriverHomeActivity
import com.taxiapp.ui.home.HomeActivity
import com.taxiapp.ui.theme.TaxiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Entry point for signed-out users: onboarding pages + sign-in. */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()
    private val googleIdTokens by lazy { GoogleIdTokenProvider(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TaxiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state) {
                    (state as? SignInState.SignedIn)?.let { openHome(it.role) }
                }

                OnboardingScreen(
                    state = state,
                    onGoogleClick = ::signInWithGoogle,
                    onPhoneClick = {
                        startActivity(Intent(this, PhoneEntryActivity::class.java))
                    },
                    onErrorShown = viewModel::onErrorShown,
                    backgroundVideo = BACKGROUND_VIDEO,
                )
            }
        }
    }

    private fun signInWithGoogle() {
        viewModel.onGoogleRequested()
        lifecycleScope.launch {
            viewModel.onGoogleResult(googleIdTokens.request())
        }
    }

    private fun openHome(role: String) {
        val target = if (role == "driver") DriverHomeActivity::class.java else HomeActivity::class.java
        startActivity(
            Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    companion object {
        /**
         * To use real footage instead of the drawn map motion, add a short muted
         * loop as res/raw/onboarding_bg.mp4 and set this to R.raw.onboarding_bg.
         */
        val BACKGROUND_VIDEO: Int? = null
    }
}
