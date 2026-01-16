package com.soundmeter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SoundMeterState(
    val currentDb: Double = 0.0,
    val minDb: Double = Double.MAX_VALUE,
    val maxDb: Double = 0.0,
    val avgDb: Double = 0.0,
    val isRunning: Boolean = false
)

class SoundMeterViewModel : ViewModel() {

    private val soundMeter = SoundMeter()

    private val _state = MutableStateFlow(SoundMeterState())
    val state: StateFlow<SoundMeterState> = _state.asStateFlow()

    private val readings = mutableListOf<Double>()

    init {
        viewModelScope.launch {
            soundMeter.decibelLevel.collect { db ->
                if (soundMeter.isRunning.value && db > 0) {
                    readings.add(db)
                    updateState(db)
                }
            }
        }

        viewModelScope.launch {
            soundMeter.isRunning.collect { running ->
                _state.value = _state.value.copy(isRunning = running)
            }
        }
    }

    private fun updateState(currentDb: Double) {
        val minDb = if (_state.value.minDb == Double.MAX_VALUE) {
            currentDb
        } else {
            minOf(_state.value.minDb, currentDb)
        }

        val maxDb = maxOf(_state.value.maxDb, currentDb)
        val avgDb = if (readings.isNotEmpty()) {
            readings.sum() / readings.size
        } else {
            0.0
        }

        _state.value = _state.value.copy(
            currentDb = currentDb,
            minDb = minDb,
            maxDb = maxDb,
            avgDb = avgDb
        )
    }

    fun startMeasuring() {
        soundMeter.start(viewModelScope)
    }

    fun stopMeasuring() {
        soundMeter.stop()
    }

    fun reset() {
        readings.clear()
        _state.value = SoundMeterState(isRunning = _state.value.isRunning)
    }

    override fun onCleared() {
        super.onCleared()
        soundMeter.release()
    }
}
