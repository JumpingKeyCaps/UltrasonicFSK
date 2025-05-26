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
import kotlin.math.sqrt

/**
 * ### FskDecoder amélioré
 *
 * Service de décodage FSK avec :
 * - Détection des marqueurs start/stop (fréquences spécifiques)
 * - Moyennage des fréquences détectées sur plusieurs fenêtres
 * - Seuil dynamique d’amplitude pour filtrer le bruit
 * - Bufferisation des bits reçus avec validation start/stop
 *
 * @param context Context Android (nécessaire pour vérifier permissions)
 * @param f0 Fréquence bit 0 (Hz)
 * @param f1 Fréquence bit 1 (Hz)
 * @param startFreq Fréquence marqueur de début message (Hz)
 * @param stopFreq Fréquence marqueur de fin message (Hz)
 * @param sampleRate Fréquence d’échantillonnage (Hz)
 * @param bitDurationMs Durée d’un bit (ms)
 * @param baseAmplitudeThresholdDb Seuil de base amplitude (dB)
 * @param smoothingWindow Nombre de fenêtres FFT pour moyennage
 */
class FskDecoder(
    private val context: Context,
    private val parser: SimpleBinaryParser, // ⬅️ nouveau paramètre
    private val f0: Double = 18500.0,
    private val f1: Double = 18700.0,
    private val startFreq: Double = 19000.0,
    private val stopFreq: Double = 19200.0,
    private val sampleRate: Int = 44100,
    private val bitDurationMs: Int = 100,
    private val baseAmplitudeThresholdDb: Double = -50.0,
    private val smoothingWindow: Int = 3,
) {

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).takeIf { it > 0 } ?: (sampleRate / 10)

    private var audioRecord: AudioRecord? = null
    private var decodingJob: Job? = null

    private val decoderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val recentFreqs = ArrayDeque<Double>(smoothingWindow)

    private var dynamicAmplitudeThresholdDb = baseAmplitudeThresholdDb

    fun startDecoding() {
        if (decodingJob != null) return

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("FskDecoder", "Permission RECORD_AUDIO non accordée. Abandon.")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        ).apply {
            if (state != AudioRecord.STATE_INITIALIZED) {
                Log.e("FskDecoder", "Échec initialisation AudioRecord")
                release()
                audioRecord = null
                return
            }
            startRecording()
        }

        decodingJob = decoderScope.launch {
            val audioBuffer = ShortArray(bufferSize)
            val doubleBuffer = DoubleArray(bufferSize)

            while (isActive) {
                val read = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    for (i in 0 until read) {
                        doubleBuffer[i] = audioBuffer[i].toDouble()
                    }

                    val freqRaw = extractDominantFrequency(doubleBuffer.copyOf(read), sampleRate)

                    if (freqRaw > 0) {
                        if (recentFreqs.size == smoothingWindow) recentFreqs.removeFirst()
                        recentFreqs.add(freqRaw)

                        val freqAvg = recentFreqs.average()
                        adjustDynamicThreshold(doubleBuffer.copyOf(read))

                        detectBitOrMarker(freqAvg)?.let { symbol ->
                            parser.onBitReceived(symbol) // ⬅️ on délègue au parser
                        }
                    }
                }
                delay(bitDurationMs.toLong())
            }
        }
    }

    fun stopDecoding() {
        decodingJob?.cancel()
        decodingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        recentFreqs.clear()
    }

    private fun adjustDynamicThreshold(buffer: DoubleArray) {
        val rms = sqrt(buffer.map { it * it }.average())
        val rmsDb = 20 * log10(rms + 1e-12)
        dynamicAmplitudeThresholdDb = (dynamicAmplitudeThresholdDb * 0.9) + (rmsDb * 0.1)

        if (dynamicAmplitudeThresholdDb > baseAmplitudeThresholdDb) {
            dynamicAmplitudeThresholdDb = baseAmplitudeThresholdDb
        }
    }

    private fun detectBitOrMarker(freq: Double): Int? {
        val tolerance = 150.0

        return when {
            abs(freq - startFreq) < tolerance -> 2  // Start = 2
            abs(freq - stopFreq) < tolerance -> 3   // Stop = 3
            abs(freq - f0) < tolerance -> 0         // Bit 0
            abs(freq - f1) < tolerance -> 1         // Bit 1
            else -> null
        }
    }

    private fun extractDominantFrequency(data: DoubleArray, sampleRate: Int): Double {
        val n = data.size
        val size = Integer.highestOneBit(n).takeIf { it == n } ?: (Integer.highestOneBit(n) shl 1)

        val padded = DoubleArray(size).apply {
            for (i in data.indices) this[i] = data[i]
        }

        val windowed = applyHammingWindow(padded)
        val real = windowed.copyOf()
        val imag = DoubleArray(size) { 0.0 }

        FFT.fft(real, imag)

        val magnitudes = DoubleArray(size / 2) { i ->
            10 * kotlin.math.log10(real[i].pow(2) + imag[i].pow(2) + 1e-12)
        }

        val maxIndex = magnitudes
            .withIndex()
            .filter { it.value > dynamicAmplitudeThresholdDb }
            .maxByOrNull { it.value }
            ?.index ?: return -1.0

        return maxIndex * sampleRate.toDouble() / size
    }

    private fun applyHammingWindow(input: DoubleArray): DoubleArray {
        val n = input.size
        return DoubleArray(n) { i ->
            val w = 0.54 - 0.46 * cos(2.0 * Math.PI * i / (n - 1))
            input[i] * w
        }
    }
}