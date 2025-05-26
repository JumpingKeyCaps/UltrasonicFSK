package com.lebaillyapp.ultrasonicfsk.data.service

import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin

/**
 * ##  FFT – Transformée de Fourier Rapide (Cooley-Tukey)
 *
 * Implémentation maison de la transformée de Fourier rapide (FFT), utilisée pour extraire les composantes fréquentielles
 * d’un signal audio dans le cadre d’un décodage FSK.
 *
 * ⚠ Fonctionne uniquement sur des tailles de tableau puissance de 2.
 *
 * ---
 * ###  API
 * - [fft] : Applique la FFT sur deux tableaux (réel et imaginaire)
 *
 * ---
 */
object FFT {

    /**
     * ##  fft
     *
     * Applique la transformée de Fourier rapide (FFT) sur deux tableaux (réel + imaginaire).
     * Modifie les tableaux *in-place*.
     *
     * ---
     * @param real Tableau contenant la partie réelle du signal d'entrée.
     * @param imag Tableau contenant la partie imaginaire du signal d'entrée.
     *
     * ---
     * @throws IllegalArgumentException si :
     * - les deux tableaux n'ont pas la même taille
     * - la taille n'est pas une puissance de 2
     *
     * ---
     * ###  Exécution :
     * - Réarrangement bit-reversé
     * - Boucles FFT avec calcul des twiddle factors
     * - In-place, sans allocation supplémentaire
     */
    fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n != imag.size) throw IllegalArgumentException("Mismatched lengths")
        if (n and (n - 1) != 0) throw IllegalArgumentException("Array length must be power of 2")

        val bits = log2(n.toDouble()).toInt()
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
            val tableStep = Math.PI * 2 / size
            for (i in 0 until n step size) {
                for (j in 0 until halfSize) {
                    val k = j + i
                    val l = k + halfSize
                    val angle = tableStep * j
                    val tReal = real[l] * cos(angle) + imag[l] * sin(angle)
                    val tImag = -real[l] * sin(angle) + imag[l] * cos(angle)
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
     * ##  reverseBits
     *
     * Inverse les bits d’un index (ex. : 3 -> 110 → 011 -> 6)
     * Utilisé pour réorganiser les entrées en ordre bit-reversé avant la FFT.
     *
     * @param index Index à inverser
     * @param bits Nombre total de bits (log2 de la taille des tableaux)
     * @return Index inversé
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