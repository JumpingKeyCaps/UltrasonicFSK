package com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.lebaillyapp.ultrasonicfsk.data.service.fsk.FFT
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ## Fsk4Decoder – Décodeur FSK 4-tons (4-FSK) avec détection start/stop
 *
 * Ce service écoute le micro, détecte 4 fréquences principales correspondant à 4 symboles
 * (0,1,2,3), plus les marqueurs start et stop, et transmet les symboles décodés au parser.
 *
 * ---
 *
 * ### Fonctionnalités principales :
 * - Détection des 4 fréquences symboles (`freq0`, `freq1`, `freq2`, `freq3`)
 * - Détection des fréquences start et stop
 * - Moyennage glissant sur plusieurs fenêtres pour stabilité
 * - Seuil d’amplitude adaptatif pour ignorer le bruit
 * - Emission des symboles décodés au [FourFskParser] (2 bits/symbole)
 *
 * ---
 *
 * @param context Contexte Android (pour vérifier permission microphone)
 * @param parser Instance de [FourFskParser] pour recevoir les symboles décodés
 * @param freq0 Fréquence symbole 0 (Hz)
 * @param freq1 Fréquence symbole 1 (Hz)
 * @param freq2 Fréquence symbole 2 (Hz)
 * @param freq3 Fréquence symbole 3 (Hz)
 * @param startFreq Fréquence marqueur début message (Hz)
 * @param stopFreq Fréquence marqueur fin message (Hz)
 * @param sampleRate Fréquence d’échantillonnage audio
 * @param bitDurationMs Durée d’un symbole (bit) en ms
 * @param baseAmplitudeThresholdDb Seuil base amplitude en dB
 * @param smoothingWindow Taille fenêtre moyennage
 */
class Fsk4Decoder(
    private val context: Context,
    private val parser: FourFskParser,
    private val freq0: Double = 18500.0,
    private val freq1: Double = 18700.0,
    private val freq2: Double = 18900.0,
    private val freq3: Double = 19100.0,
    private val startFreq: Double = 19300.0,
    private val stopFreq: Double = 19500.0,
    private val sampleRate: Int = 44100,
    private val bitDurationMs: Int = 50,
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

    /**
     * ## startDecoding
     * Démarre l’écoute micro et la détection en coroutine.
     * Analyse la fréquence dominante par fenêtre audio,
     * moyenne les valeurs pour stabilité,
     * et transmet le symbole décodé au parser.
     */
    fun startDecoding() {
        if (decodingJob != null) return

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("Fsk4Decoder", "Permission RECORD_AUDIO non accordée. Abort.")
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
                Log.e("Fsk4Decoder", "AudioRecord init failed")
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

                        detectSymbolOrMarker(freqAvg)?.let { symbol ->
                            parser.onSymbolReceived(symbol) // IMPORTANT : appel au parser 4FSK ici
                        }
                    }
                }
                delay(bitDurationMs.toLong())
            }
        }
    }

    /**
     * ## stopDecoding
     * Stoppe proprement l’écoute micro et la coroutine.
     */
    fun stopDecoding() {
        decodingJob?.cancel()
        decodingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recentFreqs.clear()
    }

    /**
     * ## adjustDynamicThreshold
     * Ajuste dynamiquement le seuil d’amplitude selon le RMS du buffer.
     */
    private fun adjustDynamicThreshold(buffer: DoubleArray) {
        val rms = sqrt(buffer.map { it * it }.average())
        val rmsDb = 20 * log10(rms + 1e-12)
        dynamicAmplitudeThresholdDb = (dynamicAmplitudeThresholdDb * 0.9) + (rmsDb * 0.1)
        if (dynamicAmplitudeThresholdDb > baseAmplitudeThresholdDb) {
            dynamicAmplitudeThresholdDb = baseAmplitudeThresholdDb
        }
    }

    /**
     * ## detectSymbolOrMarker
     * Identifie si la fréquence correspond à un symbole (0-3) ou marqueur start/stop.
     *
     * @return code symbole : 0..3 pour les bits, 4 = start, 5 = stop, null sinon
     */
    private fun detectSymbolOrMarker(freq: Double): Int? {
        val tolerance = 150.0
        return when {
            abs(freq - startFreq) < tolerance -> 4
            abs(freq - stopFreq) < tolerance -> 5
            abs(freq - freq0) < tolerance -> 0
            abs(freq - freq1) < tolerance -> 1
            abs(freq - freq2) < tolerance -> 2
            abs(freq - freq3) < tolerance -> 3
            else -> null
        }
    }

    /**
     * ## extractDominantFrequency
     * Analyse FFT pour extraire la fréquence dominante avec seuil dynamique.
     *
     * @return fréquence dominante détectée ou -1 si aucune fiable.
     */
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
            10 * log10(real[i].pow(2) + imag[i].pow(2) + 1e-12)
        }

        val maxIndex = magnitudes
            .withIndex()
            .filter { it.value > dynamicAmplitudeThresholdDb }
            .maxByOrNull { it.value }
            ?.index ?: return -1.0

        return maxIndex * sampleRate.toDouble() / size
    }

    /**
     * ## applyHammingWindow
     * Applique une fenêtre de Hamming avant FFT pour réduire les effets de bord.
     */
    private fun applyHammingWindow(input: DoubleArray): DoubleArray {
        val n = input.size
        return DoubleArray(n) { i ->
            val w = 0.54 - 0.46 * cos(2.0 * Math.PI * i / (n - 1))
            input[i] * w
        }
    }
}
