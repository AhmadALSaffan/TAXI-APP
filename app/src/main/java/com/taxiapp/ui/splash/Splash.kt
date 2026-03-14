package com.taxiapp.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.taxiapp.R
import com.taxiapp.databinding.ActivitySplashBinding
import com.taxiapp.ui.auth.WelcomeActivity
import com.taxiapp.ui.driver.DriverHomeActivity
import com.taxiapp.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val database: FirebaseDatabase  by lazy { FirebaseDatabase.getInstance() }


    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )

        startAnimations()
        simulateLoadingAndNavigate()
        hideSystemBars()
    }


    private fun startAnimations() {
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.cardHero.startAnimation(fadeIn)

        binding.imgCarIcon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(300).setDuration(600).start()

        binding.tvAppName.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(500).setDuration(600).start()

        binding.tvTagline.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(700).setDuration(600).start()

        binding.layoutProgress.animate()
            .alpha(1f).setStartDelay(900).setDuration(400).start()

        binding.layoutSecure.animate()
            .alpha(1f).setStartDelay(1100).setDuration(400).start()
    }


    private fun simulateLoadingAndNavigate() {
        lifecycleScope.launch {
            for (progress in 0..100 step 5) {
                binding.progressBar.progress   = progress
                binding.tvProgressPercent.text = "$progress%"
                delay(90L)
            }
            delay(300L)
            navigateNext()
        }
    }

    private fun navigateNext() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            go(WelcomeActivity::class.java)
            return
        }
        lifecycleScope.launch {
            val role = fetchRole(user.uid)
            if (role == "driver") go(DriverHomeActivity::class.java)
            else                  go(HomeActivity::class.java)
        }
    }

    private suspend fun fetchRole(uid: String): String {
        return try {
            val snapshot = database.getReference("users/$uid/role").get().await()
            snapshot.getValue(String::class.java) ?: "passenger"
        } catch (_: Exception) {
            "passenger"
        }
    }

    private fun go(destination: Class<*>) {
        startActivity(
            Intent(this, destination).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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