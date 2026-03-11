package com.taxiapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.taxiapp.R
import com.taxiapp.databinding.ActivityPhoneEntryBinding
import com.taxiapp.databinding.DialogCountryPickerBinding
import com.taxiapp.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class PhoneEntryActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var binding: ActivityPhoneEntryBinding
    private val viewModel: com.taxiapp.ui.auth.AuthViewModel by viewModels()

    private var selectedDialCode = "+1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeOtpSendState()
        hideSystemBars()
    }


    private fun setupClickListeners() {

        binding.btnBack.setOnClickListener { finish() }

        binding.llCountrySelector.setOnClickListener { showCountryPicker() }

        binding.btnContinue.setOnClickListener {
            val rawNumber = binding.etPhoneNumber.text.toString().trim()
            if (!isValidPhoneNumber(rawNumber)) {
                binding.etPhoneNumber.error = "Enter a valid phone number"
                return@setOnClickListener
            }
            val sanitized = rawNumber.trimStart('0')
            val e164      = "$selectedDialCode$sanitized"
            viewModel.sendOtp(e164, this)
        }
    }


    private fun isValidPhoneNumber(number: String): Boolean {
        if (number.isBlank()) return false
        val digitsOnly = number.filter { it.isDigit() }
        return digitsOnly.length in 6..15
    }


    private fun observeOtpSendState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.otpSendState.collect { state ->
                    when (state) {
                        is Resource.Loading -> showLoading(true)

                        is Resource.Success -> {
                            showLoading(false)
                            val rawNumber = binding.etPhoneNumber.text.toString().trim()
                            val e164      = "$selectedDialCode${rawNumber.trimStart('0')}"

                            val intent = Intent(
                                this@PhoneEntryActivity,
                                OtpVerificationActivity::class.java
                            )
                            intent.putExtra(OtpVerificationActivity.EXTRA_PHONE, e164)
                            intent.putExtra(OtpVerificationActivity.EXTRA_VERIFICATION_ID, state.data)
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            startActivity(intent)
                        }

                        is Resource.Error -> {
                            showLoading(false)
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }

                        null -> showLoading(false)
                    }
                }
            }
        }
    }


    private fun showLoading(show: Boolean) {
        binding.btnContinue.isEnabled = !show
        binding.btnContinue.alpha     = if (show) 0.6f else 1.0f
        binding.btnContinue.text      = if (show) "" else "Continue"
    }


    private fun showCountryPicker() {
        val dialog        = BottomSheetDialog(this, R.style.CountryPickerTheme)
        val dialogBinding = DialogCountryPickerBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val allCountries = CountryUtils.getCountries(this)

        val adapter = CountryAdapter(allCountries) { selected ->
            binding.tvFlag.text = selected.flag
            binding.tvDialCode.text = selected.dialCode
            selectedDialCode = selected.dialCode
            dialog.dismiss()
        }

        dialogBinding.rvCountries.apply {
            layoutManager = LinearLayoutManager(this@PhoneEntryActivity)
            this.adapter  = adapter
            addItemDecoration(
                DividerItemDecoration(this@PhoneEntryActivity, DividerItemDecoration.VERTICAL)
            )
        }

        dialogBinding.etSearchCountry.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { adapter.filter(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        dialog.show()
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