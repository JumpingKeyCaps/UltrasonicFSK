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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * ##  FskDecoder – Décodeur FSK binaire avec détection de start/stop
 *
 * Ce service analyse un flux audio via le micro, détecte les fréquences dominantes
 * et reconstruit une séquence binaire correspondant au message émis.
 *
 * ---
 * ###  Fonctionnalités principales :
 * - Détection des marqueurs de début (`startFreq`) et de fin (`stopFreq`)
 * - Moyennage sur plusieurs fenêtres pour fiabilité
 * - Seuil d'amplitude adaptatif pour ignorer le bruit
 * - Délégation du décodage logique au `SimpleBinaryParser`
 *
 * ---
 * @param context Contexte Android (utilisé pour vérifier la permission micro)
 * @param parser Instance de [SimpleBinaryParser] pour traiter les bits détectés
 * @param f0 Fréquence du bit 0 (en Hz)
 * @param f1 Fréquence du bit 1 (en Hz)
 * @param startFreq Fréquence spéciale pour signaler le début d’un message
 * @param stopFreq Fréquence spéciale pour signaler la fin d’un message
 * @param sampleRate Fréquence d’échantillonnage audio
 * @param bitDurationMs Durée d’un bit en millisecondes
 * @param baseAmplitudeThresholdDb Seuil de base pour ignorer le bruit (en dB)
 * @param smoothingWindow Nombre de fenêtres FFT utilisées pour la moyenne glissante
 */
class FskDecoder(
    private val context: Context,
    private val parser: SimpleBinaryParser,
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

    /**
     * ##  startDecoding
     * Lance la lecture micro et démarre le traitement audio en coroutine.
     * Détecte les fréquences dominantes à intervalles réguliers et transmet les bits à `parser`.
     */
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
                            parser.onBitReceived(symbol)
                        }
                    }
                }
                delay(bitDurationMs.toLong())
            }
        }
    }

    /**
     * ##  stopDecoding
     * Stoppe proprement l’enregistrement audio et la coroutine de décodage.
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
     * ##  adjustDynamicThreshold
     * Ajuste dynamiquement le seuil de détection à partir du niveau RMS du buffer courant.
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
     * ##  detectBitOrMarker
     * Détecte si la fréquence moyenne correspond à un bit (`0` ou `1`) ou à un marqueur (`start`, `stop`).
     *
     * @return Code symbolique : `0` pour 0, `1` pour 1, `2` pour start, `3` pour stop, `null` sinon.
     */
    private fun detectBitOrMarker(freq: Double): Int? {
        val tolerance = 150.0
        return when {
            abs(freq - startFreq) < tolerance -> 2
            abs(freq - stopFreq) < tolerance -> 3
            abs(freq - f0) < tolerance -> 0
            abs(freq - f1) < tolerance -> 1
            else -> null
        }
    }

    /**
     * ##  extractDominantFrequency
     * Applique une FFT à la fenêtre audio pour trouver la fréquence dominante utile.
     * Utilise un seuil dynamique pour ignorer les pics faibles.
     *
     * @return La fréquence dominante détectée (ou -1 si aucune significative).
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
     * ##  applyHammingWindow
     * Applique une fenêtre de Hamming pour lisser le signal avant FFT.
     */
    private fun applyHammingWindow(input: DoubleArray): DoubleArray {
        val n = input.size
        return DoubleArray(n) { i ->
            val w = 0.54 - 0.46 * cos(2.0 * Math.PI * i / (n - 1))
            input[i] * w
        }
    }
}