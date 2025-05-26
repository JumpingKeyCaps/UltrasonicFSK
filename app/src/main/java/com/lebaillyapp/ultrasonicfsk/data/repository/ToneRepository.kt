package com.lebaillyapp.ultrasonicfsk.data.repository

class ToneRepository {
    // freq0 ajustable, freq1 toujours freq0 + 200 Hz
    var freq0: Double = 18500.0
        private set

    val freq1: Double
        get() = freq0 + 200.0

    fun setFreq0(freq: Double) {
        freq0 = freq.coerceIn(20.0, 20000.0)
    }
}