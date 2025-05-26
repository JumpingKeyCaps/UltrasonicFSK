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

    private val _bitFlow = MutableSharedFlow<Int>(extraBufferCapacity = 128)
    val bitFlow: SharedFlow<Int> get() = _bitFlow.asSharedFlow()

    // Buffer circulaire pour stocker les dernières fréquences détectées pour moyennage
    private val recentFreqs = ArrayDeque<Double>(smoothingWindow)

    // État de réception message
    private var isReceivingMessage = false
    private val receivedBitsBuffer = mutableListOf<Int>()

    // Pour ajuster dynamiquement le seuil selon le bruit ambiant
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
                    // Convertir en double
                    for (i in 0 until read) {
                        doubleBuffer[i] = audioBuffer[i].toDouble()
                    }

                    // Extraire fréquence dominante
                    val freqRaw = extractDominantFrequency(doubleBuffer.copyOf(read), sampleRate)

                    if (freqRaw > 0) {
                        // Mise à jour buffer moyennage
                        if (recentFreqs.size == smoothingWindow) recentFreqs.removeFirst()
                        recentFreqs.add(freqRaw)

                        // Moyenne simple
                        val freqAvg = recentFreqs.average()

                        // Ajustement dynamique seuil amplitude (simple rolling min)
                        adjustDynamicThreshold(doubleBuffer.copyOf(read))

                        // Détecter la fréquence la plus probable
                        val detectedBitOrMarker = detectBitOrMarker(freqAvg)

                        detectedBitOrMarker?.let { symbol ->
                            handleSymbol(symbol)
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
        receivedBitsBuffer.clear()
        isReceivingMessage = false
    }

    /**
     * Ajuste dynamiquement le seuil d'amplitude en fonction du niveau sonore
     * moyen du buffer actuel, pour mieux filtrer bruit / silence.
     */
    private fun adjustDynamicThreshold(buffer: DoubleArray) {
        // On calcule le niveau RMS du buffer
        val rms = sqrt(buffer.map { it * it }.average())
        // Converti en dB approximatif
        val rmsDb = 20 * log10(rms + 1e-12)

        // Lissage simple vers le seuil dynamique
        dynamicAmplitudeThresholdDb = (dynamicAmplitudeThresholdDb * 0.9) + (rmsDb * 0.1)

        // Ne pas descendre trop bas
        if (dynamicAmplitudeThresholdDb > baseAmplitudeThresholdDb) {
            dynamicAmplitudeThresholdDb = baseAmplitudeThresholdDb
        }
    }

    /**
     * Détecte si la fréquence correspond à un bit 0/1, ou marqueur start/stop.
     * Renvoie un Int spécial pour start/stop (-1 / -2) ou 0/1 pour bit,
     * ou null si fréquence hors plage utile.
     */
    private fun detectBitOrMarker(freq: Double): Int? {
        val tolerance = 150.0

        return when {
            abs(freq - startFreq) < tolerance -> -1  // Start marker
            abs(freq - stopFreq) < tolerance -> -2   // Stop marker
            abs(freq - f0) < tolerance -> 0          // Bit 0
            abs(freq - f1) < tolerance -> 1          // Bit 1
            else -> null
        }
    }

    /**
     * Gère les symboles détectés (start, stop, bits),
     * contrôle le buffer de réception, et émet les bits validés.
     */
    private suspend fun handleSymbol(symbol: Int) {
        when (symbol) {
            -1 -> { // Marqueur start détecté
                isReceivingMessage = true
                receivedBitsBuffer.clear()
                Log.d("FskDecoder", "Début message détecté")
            }

            -2 -> { // Marqueur stop détecté
                if (isReceivingMessage) {
                    Log.d("FskDecoder", "Fin message détectée, bits reçus: ${receivedBitsBuffer.size}")
                    // TODO: Ici tu peux valider/checker le message (CRC, checksum, etc)
                    // On émet les bits du message
                    receivedBitsBuffer.forEach {
                        _bitFlow.emit(it)
                    }
                }
                isReceivingMessage = false
                receivedBitsBuffer.clear()
            }

            0, 1 -> {
                if (isReceivingMessage) {
                    receivedBitsBuffer.add(symbol)
                }
            }
        }
    }

    /**
     * Extrait la fréquence dominante d'un buffer audio via FFT.
     * Applique une fenêtre de Hamming, puis calcule magnitude en dB.
     */
    private fun extractDominantFrequency(data: DoubleArray, sampleRate: Int): Double {
        val n = data.size
        val size = Integer.highestOneBit(n).takeIf { it == n } ?: (Integer.highestOneBit(n) shl 1)

        val padded = DoubleArray(size).apply {
            for (i in data.indices) this[i] = data[i]
            for (i in data.size until size) this[i] = 0.0
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