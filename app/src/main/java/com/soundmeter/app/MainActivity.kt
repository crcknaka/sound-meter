package com.soundmeter.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.soundmeter.app.databinding.ActivityMainBinding
import com.soundmeter.app.databinding.DialogCalibrationBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SoundMeterViewModel by viewModels()

    companion object {
        private const val PREFS_NAME = "sound_meter_prefs"
        private const val KEY_CALIBRATION_OFFSET = "calibration_offset"
    }

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

        loadCalibration()
        setupClickListeners()
        observeState()

        // Auto-start measuring on app launch
        checkPermissionAndStart()
    }

    private fun loadCalibration() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val offset = prefs.getFloat(KEY_CALIBRATION_OFFSET, 0f).toDouble()
        viewModel.setCalibrationOffset(offset)
    }

    private fun saveCalibration(offset: Float) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_CALIBRATION_OFFSET, offset).apply()
        viewModel.setCalibrationOffset(offset.toDouble())
    }

    private fun setupClickListeners() {
        binding.startStopButton.setOnClickListener { view ->
            performHapticFeedback(view)
            if (viewModel.state.value.isRunning) {
                viewModel.stopMeasuring()
                updateButtonState(false)
            } else {
                checkPermissionAndStart()
            }
        }

        binding.resetButton.setOnClickListener { view ->
            performHapticFeedback(view)
            viewModel.reset()
            binding.soundWaveView.reset()
            updateStatsDisplay(SoundMeterState())
        }

        binding.calibrationButton.setOnClickListener { view ->
            performHapticFeedback(view)
            showCalibrationDialog()
        }
    }

    private fun showCalibrationDialog() {
        val dialogBinding = DialogCalibrationBinding.inflate(LayoutInflater.from(this))

        val currentOffset = viewModel.getCalibrationOffset().toFloat()
        dialogBinding.calibrationSlider.value = currentOffset
        dialogBinding.offsetValueText.text = getString(R.string.calibration_offset, currentOffset)

        dialogBinding.calibrationSlider.addOnChangeListener { _, value, _ ->
            dialogBinding.offsetValueText.text = getString(R.string.calibration_offset, value)
        }

        dialogBinding.resetCalibrationButton.setOnClickListener {
            dialogBinding.calibrationSlider.value = 0f
        }

        val dialog = AlertDialog.Builder(this, R.style.CalibrationDialog)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.saveButton.setOnClickListener {
            val newOffset = dialogBinding.calibrationSlider.value
            saveCalibration(newOffset)
            Toast.makeText(this, R.string.calibration_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun performHapticFeedback(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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

                // Feed data to the graph
                if (state.isRunning && state.currentDb > 0) {
                    binding.soundWaveView.addReading(state.currentDb.toFloat())
                }
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
            binding.startStopButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.level_danger)
        } else {
            binding.startStopButton.text = getString(R.string.start)
            binding.startStopButton.setIconResource(R.drawable.ic_play)
            binding.startStopButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }
        // Keep text and icon white
        binding.startStopButton.setTextColor(ContextCompat.getColor(this, R.color.white))
        binding.startStopButton.iconTint = ContextCompat.getColorStateList(this, R.color.white)
    }

    override fun onStop() {
        super.onStop()
        if (viewModel.state.value.isRunning) {
            viewModel.stopMeasuring()
            updateButtonState(false)
        }
    }
}
