package com.lebaillyapp.ultrasonicfsk.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.ultrasonicfsk.data.repository.ToneRepository
import com.lebaillyapp.ultrasonicfsk.data.service.tone.TonePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ToneTestViewModel(
    private val repo: ToneRepository = ToneRepository(),
    private val player: TonePlayer = TonePlayer()
) : ViewModel() {

    private val _freq0 = MutableStateFlow(repo.freq0)
    val freq0: StateFlow<Double> = _freq0

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying


    val waveform = MutableStateFlow<List<Float>>(emptyList())
    val fftData = MutableStateFlow<List<Float>>(emptyList())
    val fskToneHistory = MutableStateFlow<List<Double>>(emptyList())
    val spectrogramHistory = mutableStateListOf<List<Float>>()




    fun updateFreq0(newFreq: Double) {
        repo.setFreq0(newFreq)
        _freq0.value = repo.freq0
    }

    fun playTone() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        player.playTone(repo.freq0, 5000, viewModelScope)
        viewModelScope.launch {
            delay(5000)
            _isPlaying.value = false
        }
    }

    fun stopTone() {
        player.stop()
        _isPlaying.value = false
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
    }
}