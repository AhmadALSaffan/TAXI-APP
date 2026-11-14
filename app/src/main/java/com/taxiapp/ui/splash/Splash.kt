package com.taxiapp.ui.splash

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.taxiapp.ui.driver.DriverHomeActivity
import com.taxiapp.ui.home.HomeActivity
import com.taxiapp.ui.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Launch screen. The system shows the animated mark (Theme.TAXIAPP.Starting)
 * while we work out where to send the user: onboarding when signed out,
 * otherwise the rider or driver home.
 */
@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var database: FirebaseDatabase

    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }

        lifecycleScope.launch {
            val startedAt = SystemClock.uptimeMillis()
            val destination = resolveDestination()

            // Let the icon animation finish instead of cutting it off.
            val shown = SystemClock.uptimeMillis() - startedAt
            if (shown < MIN_VISIBLE_MS) delay(MIN_VISIBLE_MS - shown)

            ready = true
            startActivity(
                Intent(this@SplashActivity, destination)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                ActivityOptions.makeCustomAnimation(
                    this@SplashActivity, android.R.anim.fade_in, android.R.anim.fade_out,
                ).toBundle(),
            )
            finish()
        }
    }

    private suspend fun resolveDestination(): Class<*> {
        val user = auth.currentUser ?: return OnboardingActivity::class.java
        val role = withTimeoutOrNull(ROLE_TIMEOUT_MS) {
            runCatching {
                database.getReference("users/${user.uid}/role").get().await().getValue(String::class.java)
            }.getOrNull()
        }
        return if (role == "driver") DriverHomeActivity::class.java else HomeActivity::class.java
    }

    private companion object {
        const val MIN_VISIBLE_MS = 850L
        const val ROLE_TIMEOUT_MS = 4_000L
    }
}
