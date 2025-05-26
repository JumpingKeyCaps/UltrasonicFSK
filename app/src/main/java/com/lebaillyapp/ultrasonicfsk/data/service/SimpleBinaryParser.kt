package com.lebaillyapp.ultrasonicfsk.data.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Parser simple binaire pour décoder un flux de bits en messages ASCII.
 *
 * Detecte start (bit 2) et stop (bit 3) pour encadrer un message,
 * puis convertit les bits en texte ASCII 8 bits par caractère.
 */
class SimpleBinaryParser(
    private val startBit: Int = 2,
    private val stopBit: Int = 3
) {

    private val _messageFlow = MutableSharedFlow<String>()
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    private val bitBuffer = mutableListOf<Int>()
    private var isRecording = false

    /**
     * Call this to process each incoming bit from the decoder.
     */
    suspend fun onBitReceived(bit: Int) {
        when {
            bit == startBit && !isRecording -> {
                // Start new message
                bitBuffer.clear()
                isRecording = true
            }
            bit == stopBit && isRecording -> {
                // Stop message, parse buffer
                val msg = bitsToAscii(bitBuffer)
                _messageFlow.emit(msg)
                isRecording = false
                bitBuffer.clear()
            }
            isRecording -> {
                // Collect bits only if recording
                bitBuffer.add(bit)
            }
            else -> {
                // Ignore bits outside message
            }
        }
    }

    /**
     * Convertit une liste de bits (8 bits par caractère) en chaîne ASCII.
     */
    private fun bitsToAscii(bits: List<Int>): String {
        val sb = StringBuilder()
        val bytesCount = bits.size / 8
        for (i in 0 until bytesCount) {
            val byteBits = bits.subList(i * 8, i * 8 + 8)
            val byteValue = byteBits.fold(0) { acc, bit -> (acc shl 1) or bit }
            sb.append(byteValue.toChar())
        }
        return sb.toString()
    }
}