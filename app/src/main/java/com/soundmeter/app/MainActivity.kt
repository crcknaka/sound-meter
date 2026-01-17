package com.soundmeter.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.soundmeter.app.databinding.ActivityMainBinding
import com.soundmeter.app.databinding.DialogCalibrationBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SoundMeterViewModel by viewModels()
    private lateinit var gestureDetector: GestureDetector

    // View modes: 0 = digital, 1 = speedometer, 2 = VU meter, 3 = circular, 4 = oscilloscope
    private var currentViewMode = 0
    private val totalViewModes = 5

    // Calibration gesture
    private var isCalibrating = false
    private var calibrationStartY = 0f
    private var calibrationStartX = 0f
    private var calibrationStartOffset = 0f
    private var lastHapticValue = 0
    private var hideIndicatorRunnable: Runnable? = null

    companion object {
        private const val PREFS_NAME = "sound_meter_prefs"
        private const val KEY_CALIBRATION_OFFSET = "calibration_offset"
        private const val KEY_VIEW_MODE = "view_mode"
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

        loadPreferences()
        setupClickListeners()
        observeState()
        updateViewMode()

        // Auto-start measuring on app launch
        checkPermissionAndStart()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val offset = prefs.getFloat(KEY_CALIBRATION_OFFSET, 0f).toDouble()
        viewModel.setCalibrationOffset(offset)
        currentViewMode = prefs.getInt(KEY_VIEW_MODE, 0)
    }

    private fun saveCalibration(offset: Float) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_CALIBRATION_OFFSET, offset).apply()
        viewModel.setCalibrationOffset(offset.toDouble())
    }

    private fun saveViewMode() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_VIEW_MODE, currentViewMode).apply()
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
            binding.speedometerView.reset()
            binding.vuMeterView.reset()
            binding.circularGaugeView.reset()
            binding.oscilloscopeView.reset()
            updateStatsDisplay(SoundMeterState())
        }

        binding.settingsButton.setOnClickListener { view ->
            performHapticFeedback(view)
            showSettingsMenu(view)
        }

        setupSwipeGesture()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showSettingsMenu(anchor: View) {
        val bottomSheet = BottomSheetDialog(this, R.style.BottomSheetDialog)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_settings, null)
        bottomSheet.setContentView(sheetView)

        // Update current view mode text
        val viewModeNames = arrayOf(
            getString(R.string.view_digital),
            getString(R.string.view_speedometer),
            getString(R.string.view_vu_meter),
            getString(R.string.view_circular),
            getString(R.string.view_oscilloscope)
        )
        sheetView.findViewById<TextView>(R.id.currentViewModeText).text = viewModeNames[currentViewMode]

        // Update current calibration text
        val offset = viewModel.getCalibrationOffset().toInt()
        val sign = if (offset >= 0) "+" else ""
        sheetView.findViewById<TextView>(R.id.currentCalibrationText).text = "Offset: ${sign}${offset} dB"

        // View Mode click
        sheetView.findViewById<View>(R.id.itemViewMode).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bottomSheet.dismiss()
            showViewModeDialog()
        }

        // Calibration click
        sheetView.findViewById<View>(R.id.itemCalibration).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bottomSheet.dismiss()
            showCalibrationDialog()
        }

        // Privacy Policy click
        sheetView.findViewById<View>(R.id.itemPrivacyPolicy).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bottomSheet.dismiss()
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        bottomSheet.show()
    }

    private fun showViewModeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_mode, null)

        val dialog = AlertDialog.Builder(this, R.style.CalibrationDialog)
            .setView(dialogView)
            .create()

        // Get all option views and their check/uncheck icons
        val options = listOf(
            Triple(dialogView.findViewById<View>(R.id.optionDigital),
                   dialogView.findViewById<ImageView>(R.id.checkDigital),
                   dialogView.findViewById<ImageView>(R.id.uncheckDigital)),
            Triple(dialogView.findViewById<View>(R.id.optionSpeedometer),
                   dialogView.findViewById<ImageView>(R.id.checkSpeedometer),
                   dialogView.findViewById<ImageView>(R.id.uncheckSpeedometer)),
            Triple(dialogView.findViewById<View>(R.id.optionVuMeter),
                   dialogView.findViewById<ImageView>(R.id.checkVuMeter),
                   dialogView.findViewById<ImageView>(R.id.uncheckVuMeter)),
            Triple(dialogView.findViewById<View>(R.id.optionCircular),
                   dialogView.findViewById<ImageView>(R.id.checkCircular),
                   dialogView.findViewById<ImageView>(R.id.uncheckCircular)),
            Triple(dialogView.findViewById<View>(R.id.optionOscilloscope),
                   dialogView.findViewById<ImageView>(R.id.checkOscilloscope),
                   dialogView.findViewById<ImageView>(R.id.uncheckOscilloscope))
        )

        // Update UI to show current selection
        fun updateSelection(selectedIndex: Int) {
            options.forEachIndexed { index, (_, checkIcon, uncheckIcon) ->
                if (index == selectedIndex) {
                    checkIcon.visibility = View.VISIBLE
                    uncheckIcon.visibility = View.GONE
                } else {
                    checkIcon.visibility = View.GONE
                    uncheckIcon.visibility = View.VISIBLE
                }
            }
        }

        updateSelection(currentViewMode)

        // Set click listeners
        options.forEachIndexed { index, (optionView, _, _) ->
            optionView.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                currentViewMode = index
                updateViewMode()
                saveViewMode()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (isCalibrating) return false

                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)

                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    if (diffX > 0) {
                        // Swipe right - previous view
                        currentViewMode = if (currentViewMode == 0) totalViewModes - 1 else currentViewMode - 1
                    } else {
                        // Swipe left - next view
                        currentViewMode = (currentViewMode + 1) % totalViewModes
                    }
                    performHapticFeedback(binding.displayContainer)
                    updateViewMode()
                    saveViewMode()
                    return true
                }
                return false
            }
        })

        binding.displayContainer.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    calibrationStartY = event.y
                    calibrationStartX = event.x
                    calibrationStartOffset = viewModel.getCalibrationOffset().toFloat()
                    isCalibrating = false
                    lastHapticValue = calibrationStartOffset.toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffY = calibrationStartY - event.y
                    val diffX = abs(event.x - calibrationStartX)

                    // Start calibration ONLY if:
                    // 1. Vertical movement > 70px (significant vertical)
                    // 2. Horizontal drift is very small (< 30px) - nearly straight vertical
                    // 3. Vertical movement is at least 3x the horizontal drift
                    if (!isCalibrating && abs(diffY) > 70 && diffX < 30 && abs(diffY) > diffX * 3) {
                        isCalibrating = true
                        showCalibrationIndicator()
                    }

                    if (isCalibrating) {
                        // Calculate new offset: swipe up = increase, swipe down = decrease
                        val sensitivity = 0.05f // dB per pixel
                        val rawOffset = calibrationStartOffset + diffY * sensitivity
                        val newOffset = rawOffset.toInt().coerceIn(-20, 20)

                        // Update calibration only when value changes
                        if (newOffset != lastHapticValue) {
                            viewModel.setCalibrationOffset(newOffset.toDouble())
                            updateCalibrationIndicator(newOffset)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            lastHapticValue = newOffset
                        }
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isCalibrating) {
                        // Save calibration
                        val finalOffset = viewModel.getCalibrationOffset().toInt()
                        saveCalibration(finalOffset.toFloat())
                        hideCalibrationIndicator()
                        isCalibrating = false
                        return@setOnTouchListener true
                    }
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showCalibrationIndicator() {
        hideIndicatorRunnable?.let { binding.calibrationIndicatorCard.removeCallbacks(it) }
        binding.calibrationIndicatorCard.animate()
            .alpha(1f)
            .setDuration(150)
            .start()
    }

    private fun updateCalibrationIndicator(offset: Int) {
        val sign = if (offset >= 0) "+" else ""
        binding.calibrationIndicator.text = "${sign}${offset} dB"

        // Update card background color based on offset
        val colorRes = when {
            offset < -5 -> R.color.level_low
            offset > 5 -> R.color.level_high
            else -> R.color.level_medium
        }
        binding.calibrationIndicatorCard.setCardBackgroundColor(
            ContextCompat.getColor(this, colorRes)
        )
    }

    private fun hideCalibrationIndicator() {
        hideIndicatorRunnable = Runnable {
            binding.calibrationIndicatorCard.animate()
                .alpha(0f)
                .setDuration(300)
                .start()
        }
        binding.calibrationIndicatorCard.postDelayed(hideIndicatorRunnable, 800)
    }

    private fun updateViewMode() {
        binding.digitalCard.visibility = if (currentViewMode == 0) View.VISIBLE else View.GONE
        binding.speedometerCard.visibility = if (currentViewMode == 1) View.VISIBLE else View.GONE
        binding.vuMeterCard.visibility = if (currentViewMode == 2) View.VISIBLE else View.GONE
        binding.circularCard.visibility = if (currentViewMode == 3) View.VISIBLE else View.GONE
        binding.oscilloscopeCard.visibility = if (currentViewMode == 4) View.VISIBLE else View.GONE
    }

    private fun showCalibrationDialog() {
        val dialogBinding = DialogCalibrationBinding.inflate(LayoutInflater.from(this))

        val currentOffset = viewModel.getCalibrationOffset().toInt().toFloat()
        var lastSliderValue = currentOffset.toInt()
        dialogBinding.calibrationSlider.value = currentOffset
        dialogBinding.calibrationSlider.stepSize = 1f
        updateCalibrationDialogText(dialogBinding, currentOffset.toInt())

        dialogBinding.calibrationSlider.addOnChangeListener { slider, value, fromUser ->
            val intValue = value.toInt()
            updateCalibrationDialogText(dialogBinding, intValue)
            // Haptic feedback when value changes
            if (fromUser && intValue != lastSliderValue) {
                slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastSliderValue = intValue
            }
        }

        dialogBinding.resetCalibrationButton.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dialogBinding.calibrationSlider.value = 0f
        }

        val dialog = AlertDialog.Builder(this, R.style.CalibrationDialog)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.saveButton.setOnClickListener {
            val newOffset = dialogBinding.calibrationSlider.value.toInt()
            saveCalibration(newOffset.toFloat())
            viewModel.setCalibrationOffset(newOffset.toDouble())
            Toast.makeText(this, R.string.calibration_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateCalibrationDialogText(dialogBinding: DialogCalibrationBinding, offset: Int) {
        val sign = if (offset >= 0) "+" else ""
        dialogBinding.offsetValueText.text = "Offset: ${sign}${offset} dB"
    }

    private fun performHapticFeedback(view: View) {
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

                // Feed data to all views
                if (state.isRunning && state.currentDb > 0) {
                    val db = state.currentDb.toFloat()
                    binding.soundWaveView.addReading(db)
                    binding.speedometerView.updateDb(db)
                    binding.vuMeterView.updateDb(db)
                    binding.circularGaugeView.updateDb(db)
                    binding.oscilloscopeView.updateDb(db)
                }
            }
        }
    }

    private fun updateDecibelDisplay(db: Double) {
        binding.decibelText.text = String.format("%.0f", db)

        val color = when {
            db < 40 -> ContextCompat.getColor(this, R.color.level_low)
            db < 60 -> ContextCompat.getColor(this, R.color.level_medium)
            db < 80 -> ContextCompat.getColor(this, R.color.level_high)
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
            db < 40 -> Pair(getString(R.string.level_quiet), R.color.level_low)
            db < 60 -> Pair(getString(R.string.level_moderate), R.color.level_medium)
            db < 80 -> Pair(getString(R.string.level_loud), R.color.level_high)
            else -> Pair(getString(R.string.level_very_loud), R.color.level_danger)
        }

        binding.levelIndicator.text = text
        binding.levelIndicatorCard.setCardBackgroundColor(ContextCompat.getColor(this, colorRes))
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
