package com.soundmeter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.soundmeter.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SoundMeterViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startMeasuring()
            updateButtonState(true)
        } else {
            Toast.makeText(
                this,
                getString(R.string.permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        binding.startStopButton.setOnClickListener {
            if (viewModel.state.value.isRunning) {
                viewModel.stopMeasuring()
                updateButtonState(false)
            } else {
                checkPermissionAndStart()
            }
        }

        binding.resetButton.setOnClickListener {
            viewModel.reset()
            updateStatsDisplay(SoundMeterState())
        }

        binding.proButton.setOnClickListener {
            startActivity(Intent(this, ProFeaturesActivity::class.java))
        }
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.startMeasuring()
                updateButtonState(true)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(
                    this,
                    getString(R.string.permission_rationale),
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                updateDecibelDisplay(state.currentDb)
                updateStatsDisplay(state)
                updateLevelIndicator(state.currentDb)
            }
        }
    }

    private fun updateDecibelDisplay(db: Double) {
        binding.decibelText.text = String.format("%.0f", db)

        val color = when {
            db < 50 -> ContextCompat.getColor(this, R.color.level_low)
            db < 70 -> ContextCompat.getColor(this, R.color.level_medium)
            db < 85 -> ContextCompat.getColor(this, R.color.level_high)
            else -> ContextCompat.getColor(this, R.color.level_danger)
        }
        binding.decibelText.setTextColor(color)
    }

    private fun updateStatsDisplay(state: SoundMeterState) {
        binding.minText.text = if (state.minDb == Double.MAX_VALUE) {
            "--"
        } else {
            String.format("%.0f", state.minDb)
        }

        binding.avgText.text = if (state.avgDb == 0.0) {
            "--"
        } else {
            String.format("%.0f", state.avgDb)
        }

        binding.maxText.text = if (state.maxDb == 0.0) {
            "--"
        } else {
            String.format("%.0f", state.maxDb)
        }
    }

    private fun updateLevelIndicator(db: Double) {
        val (text, colorRes) = when {
            db < 50 -> Pair(getString(R.string.level_quiet), R.color.level_low)
            db < 70 -> Pair(getString(R.string.level_moderate), R.color.level_medium)
            db < 85 -> Pair(getString(R.string.level_loud), R.color.level_high)
            else -> Pair(getString(R.string.level_very_loud), R.color.level_danger)
        }

        binding.levelIndicator.text = text

        val background = binding.levelIndicator.background as? GradientDrawable
            ?: GradientDrawable().apply {
                cornerRadius = 12f * resources.displayMetrics.density
            }
        background.setColor(ContextCompat.getColor(this, colorRes))
        binding.levelIndicator.background = background
    }

    private fun updateButtonState(isRunning: Boolean) {
        if (isRunning) {
            binding.startStopButton.text = getString(R.string.stop)
            binding.startStopButton.setIconResource(R.drawable.ic_stop)
        } else {
            binding.startStopButton.text = getString(R.string.start)
            binding.startStopButton.setIconResource(R.drawable.ic_play)
        }
    }

    override fun onStop() {
        super.onStop()
        if (viewModel.state.value.isRunning) {
            viewModel.stopMeasuring()
            updateButtonState(false)
        }
    }
}
