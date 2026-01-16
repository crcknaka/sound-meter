package com.soundmeter.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.soundmeter.app.databinding.ActivityProBinding

class ProFeaturesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set version text
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.versionText.text = getString(R.string.version, versionName)
        } catch (e: Exception) {
            binding.versionText.text = getString(R.string.version, "1.0.0")
        }

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.privacyPolicyButton.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
    }
}
