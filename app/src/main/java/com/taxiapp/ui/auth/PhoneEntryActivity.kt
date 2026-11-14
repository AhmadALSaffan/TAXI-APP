package com.taxiapp.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taxiapp.ui.auth.phone.PhoneSignInScreen
import com.taxiapp.ui.auth.phone.PhoneSignInViewModel
import com.taxiapp.ui.auth.phone.PhoneStep
import com.taxiapp.ui.driver.DriverHomeActivity
import com.taxiapp.ui.home.HomeActivity
import com.taxiapp.ui.theme.TaxiTheme
import dagger.hilt.android.AndroidEntryPoint

/** Phone sign-in: number → SMS code, in one screen so the two steps share state. */
@AndroidEntryPoint
class PhoneEntryActivity : ComponentActivity() {

    private val viewModel: PhoneSignInViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TaxiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Back from the code step returns to the number, not the previous screen.
                BackHandler(enabled = state.step == PhoneStep.Code) { viewModel.editNumber() }

                LaunchedEffect(state.signedInRole) {
                    state.signedInRole?.let(::openHome)
                }

                PhoneSignInScreen(
                    state = state,
                    countries = viewModel.countries,
                    onBack = ::finish,
                    onDigitsChange = viewModel::onDigitsChange,
                    onCountrySelected = viewModel::onCountrySelected,
                    onSendCode = { viewModel.sendCode(this) },
                    onCodeChange = viewModel::onCodeChange,
                    onResend = { viewModel.resend(this) },
                    onEditNumber = viewModel::editNumber,
                    onMessageShown = viewModel::onMessageShown,
                )
            }
        }
    }

    private fun openHome(role: String) {
        val target = if (role == "driver") DriverHomeActivity::class.java else HomeActivity::class.java
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}
