package com.lebaillyapp.ultrasonicfsk.data.service.fsk

import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin

/**
 * ## FFT – Transformée de Fourier Rapide (Cooley-Tukey) corrigée
 *
 * Implémentation maison de la FFT et IFFT.
 * ⚠ Fonctionne uniquement sur des tailles puissances de 2.
 *
 * API :
 * - fft(real, imag) : FFT directe
 * - ifft(real, imag) : FFT inverse
 */
object FFT {

    /**
     * ## fft
     * Transformée de Fourier rapide directe.
     * Modifie les tableaux in-place.
     */
    fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n == imag.size) { "Mismatched lengths" }
        require(n and (n - 1) == 0) { "Array length must be power of 2" }

        val bits = log2(n.toDouble()).toInt()
        // Bit reversal
        for (i in 0 until n) {
            val j = reverseBits(i, bits)
            if (j > i) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }

        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val tableStep = (2.0 * Math.PI) / size
            for (i in 0 until n step size) {
                for (j in 0 until halfSize) {
                    val k = i + j
                    val l = k + halfSize
                    val angle = tableStep * j

                    // ⚠ Signe négatif sur sin(angle) = FFT directe
                    val tReal = real[l] * cos(angle) - imag[l] * sin(angle)
                    val tImag = real[l] * sin(angle) + imag[l] * cos(angle)

                    real[l] = real[k] - tReal
                    imag[l] = imag[k] - tImag
                    real[k] += tReal
                    imag[k] += tImag
                }
            }
            size *= 2
        }
    }

    /**
     * ## ifft
     * Transformée de Fourier rapide inverse.
     * Modifie les tableaux in-place.
     * Normalise le résultat.
     */
    fun ifft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n == imag.size) { "Mismatched lengths" }
        require(n and (n - 1) == 0) { "Array length must be power of 2" }

        // Conjugué complexe
        for (i in 0 until n) {
            imag[i] = -imag[i]
        }

        // Applique FFT directe sur conjugué
        fft(real, imag)

        // Conjugué de nouveau + normalisation
        for (i in 0 until n) {
            real[i] = real[i] / n
            imag[i] = -imag[i] / n
        }
    }

    /**
     * ## reverseBits
     * Inverse les bits d’un index.
     */
    private fun reverseBits(index: Int, bits: Int): Int {
        var i = index
        var rev = 0
        for (j in 0 until bits) {
            rev = (rev shl 1) or (i and 1)
            i = i shr 1
        }
        return rev
    }
}