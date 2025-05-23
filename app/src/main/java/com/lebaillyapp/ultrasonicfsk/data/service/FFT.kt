package com.lebaillyapp.ultrasonicfsk.data.service

import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin

object FFT {

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