package com.soundmeter.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.soundmeter.app.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animate icon
        binding.splashIcon.alpha = 0f
        binding.splashIcon.scaleX = 0.5f
        binding.splashIcon.scaleY = 0.5f
        binding.splashIcon.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .start()

        // Animate title
        binding.splashTitle.alpha = 0f
        binding.splashTitle.translationY = 30f
        binding.splashTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(300)
            .setDuration(400)
            .start()

        // Animate subtitle
        binding.splashSubtitle.alpha = 0f
        binding.splashSubtitle.animate()
            .alpha(1f)
            .setStartDelay(500)
            .setDuration(400)
            .start()

        // Navigate to MainActivity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1500)
    }
}
