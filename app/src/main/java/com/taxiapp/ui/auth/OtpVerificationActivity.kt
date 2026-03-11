package com.taxiapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.taxiapp.databinding.ActivityOtpVerificationBinding
import com.taxiapp.ui.home.HomeActivity
import com.taxiapp.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OtpVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHONE = "EXTRA_PHONE"
        const val EXTRA_VERIFICATION_ID = "EXTRA_VERIFICATION_ID"
    }

    private lateinit var binding: ActivityOtpVerificationBinding
    private val viewModel: AuthViewModel by viewModels()

    private var phoneNumber: String = ""
    private var verificationId: String = ""
    private var countDownTimer: CountDownTimer? = null

    private val otpBoxes: List<EditText> by lazy {
        listOf(
            binding.etOtp1, binding.etOtp2, binding.etOtp3,
            binding.etOtp4, binding.etOtp5, binding.etOtp6
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        phoneNumber = intent.getStringExtra(EXTRA_PHONE) ?: ""
        verificationId = intent.getStringExtra(EXTRA_VERIFICATION_ID) ?: ""
        binding.tvPhoneNumber.text = phoneNumber

        setupOtpBoxes()
        startResendCountdown()
        observeVerifyState()
        observeRoleState()
        observeResendState()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnVerify.setOnClickListener { submitOtp() }

        binding.tvResendCode.setOnClickListener {
            if (binding.tvResendCode.isEnabled) {
                viewModel.sendOtp(phoneNumber, this)
                startResendCountdown()
                showMessage("Code resent to $phoneNumber")
            }
        }
    }

    private fun setupOtpBoxes() {
        otpBoxes.forEachIndexed { index, box ->
            box.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        if (index < otpBoxes.lastIndex) {
                            otpBoxes[index + 1].requestFocus()
                        } else {
                            submitOtp()
                        }
                    }
                }
            })

            box.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL
                    && event.action == KeyEvent.ACTION_DOWN
                    && box.text.isEmpty()
                    && index > 0
                ) {
                    otpBoxes[index - 1].apply {
                        requestFocus()
                        text.clear()
                    }
                    true
                } else false
            }
        }
        otpBoxes[0].requestFocus()
    }

    private fun getOtpCode() = otpBoxes.joinToString("") { it.text.toString() }

    private fun submitOtp() {
        val code = getOtpCode()
        if (code.length < 6) {
            Snackbar.make(binding.root, "Please enter all 6 digits", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (verificationId.isEmpty()) {
            Snackbar.make(binding.root, "Verification session expired. Please resend.", Snackbar.LENGTH_LONG).show()
            return
        }
        viewModel.verifyOtp(verificationId, code)
    }

    private fun startResendCountdown() {
        binding.tvResendCode.isEnabled = false
        binding.tvResendCode.alpha = 0.4f
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                binding.tvResendLabel.text = "Resend code in ${sec}s"
            }
            override fun onFinish() {
                binding.tvResendLabel.text = "Didn't receive a code?"
                binding.tvResendCode.isEnabled = true
                binding.tvResendCode.alpha = 1f
            }
        }.start()
    }


    private fun observeResendState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.otpSendState.collect { state ->
                    if (state is Resource.Success) {
                        verificationId = state.data
                    }
                }
            }
        }
    }

    private fun observeVerifyState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.otpVerifyState.collect { state ->
                    when (state) {
                        is Resource.Loading -> showLoading(true)
                        is Resource.Success -> {
                            viewModel.fetchUserRole()
                        }
                        is Resource.Error -> {
                            showLoading(false)
                            clearOtpBoxes()
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            viewModel.resetOtpVerifyState()
                        }
                        null -> showLoading(false)
                    }
                }
            }
        }
    }

    private fun observeRoleState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userRoleState.collect { state ->
                    when (state) {
                        is Resource.Loading -> showLoading(true)
                        is Resource.Success -> {
                            showLoading(false)
                            viewModel.resetOtpVerifyState()
                            viewModel.resetRoleState()
                            routeByRole(state.data)
                        }
                        is Resource.Error -> {
                            showLoading(false)
                            viewModel.resetOtpVerifyState()
                            viewModel.resetRoleState()
                            routeByRole("passenger")
                        }
                        null -> { /* waiting */ }
                    }
                }
            }
        }
    }

    private fun routeByRole(role: String) {
        val destination = when (role) {
            // "driver" -> Intent(this, DriverHomeActivity::class.java)
            else -> Intent(this, HomeActivity::class.java)
        }
        destination.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(destination)
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnVerify.isEnabled = !show
        otpBoxes.forEach { it.isEnabled = !show }
    }

    private fun clearOtpBoxes() {
        otpBoxes.forEach { it.text.clear() }
        otpBoxes[0].requestFocus()
    }

    private fun showMessage(msg: String) =
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
