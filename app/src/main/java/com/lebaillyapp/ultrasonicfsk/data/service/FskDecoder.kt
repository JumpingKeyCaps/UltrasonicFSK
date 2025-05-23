package com.lebaillyapp.ultrasonicfsk.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow

/**
 * Service FSKDecoder pour décoder un flux audio en bits via FSK (Frequency Shift Keying).
 *
 * @param context Context Android nécessaire pour vérifier les permissions.
 * @param f0 Fréquence correspondant au bit 0 (par défaut 18500 Hz).
 * @param f1 Fréquence correspondant au bit 1 (par défaut 18700 Hz).
 * @param sampleRate Fréquence d'échantillonnage audio (par défaut 44100 Hz).
 * @param bitDurationMs Durée d'un bit en millisecondes (par défaut 100 ms).
 * @param amplitudeThresholdDb Seuil en dB en dessous duquel on ignore les fréquences faibles.
 */
class FskDecoder(
    private val context: Context,
    private val f0: Double = 18500.0,
    private val f1: Double = 18700.0,
    private val sampleRate: Int = 44100,
    private val bitDurationMs: Int = 100,
    private val amplitudeThresholdDb: Double = -50.0
) {

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).takeIf { it != AudioRecord.ERROR && it != AudioRecord.ERROR_BAD_VALUE } ?: (sampleRate / 10)

    private var record: AudioRecord? = null
    private var decodingJob: Job? = null

    private val _bitFlow = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val bitFlow: SharedFlow<Int> = _bitFlow.asSharedFlow()

    // Scope dédié au décodage pour pouvoir cancel proprement
    private val decoderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startDecoding() {
        if (decodingJob != null) return

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("FSKDecoder", "RECORD_AUDIO permission not granted. Aborting decoding.")
            return
        }

        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        ).apply {
            if (state != AudioRecord.STATE_INITIALIZED) {
                Log.e("FSKDecoder", "AudioRecord initialization failed.")
                release()
                record = null
                return
            }
            startRecording()
        }

        decodingJob = decoderScope.launch {
            val audioBuffer = ShortArray(bufferSize)
            val floatBuffer = DoubleArray(bufferSize)

            while (isActive) {
                val read = record?.read(audioBuffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    // Conversion en Double et application fenêtre de Hamming
                    for (i in 0 until read) {
                        floatBuffer[i] = audioBuffer[i].toDouble()
                    }

                    val freq = extractDominantFrequency(floatBuffer.copyOf(read), sampleRate)
                    val bit = when {
                        abs(freq - f0) < 100 -> 0
                        abs(freq - f1) < 100 -> 1
                        else -> null
                    }

                    bit?.let {
                        _bitFlow.emit(it)
                    }
                }
                delay(bitDurationMs.toLong())
            }
        }
    }

    fun stopDecoding() {
        decodingJob?.cancel()
        decodingJob = null
        record?.stop()
        record?.release()
        record = null
    }

    /**
     * Extrait la fréquence dominante dans un signal audio (buffer) en utilisant la FFT.
     * Applique une fenêtre de Hamming pour réduire les effets de bords.
     * Retourne la fréquence dominante en Hz, ou -1 si aucune fréquence au-dessus du seuil.
     */
    private fun extractDominantFrequency(data: DoubleArray, sampleRate: Int): Double {
        val n = data.size

        // Vérifier que n est une puissance de 2 sinon on ajuste
        val size = Integer.highestOneBit(n).takeIf { it == n } ?: (Integer.highestOneBit(n) shl 1)

        val windowed = applyHammingWindow(data.copyOf(size).apply { fill(0.0, n, size) }) // zéro-padding si besoin
        val real = windowed.copyOf()
        val imag = DoubleArray(size) { 0.0 }

        FFT.fft(real, imag)

        val magnitudes = DoubleArray(size / 2)
        for (i in magnitudes.indices) {
            magnitudes[i] = 10 * log10(real[i].pow(2) + imag[i].pow(2) + 1e-12) // éviter log(0)
        }

        val maxIndex = magnitudes.indices
            .filter { magnitudes[it] > amplitudeThresholdDb }
            .maxByOrNull { magnitudes[it] } ?: return -1.0

        return maxIndex * sampleRate.toDouble() / size
    }

    private fun applyHammingWindow(input: DoubleArray): DoubleArray {
        val n = input.size
        return input.mapIndexed { i, v ->
            val w = 0.54 - 0.46 * cos(2.0 * Math.PI * i / (n - 1))
            v * w
        }.toDoubleArray()
    }
}