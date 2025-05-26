package com.lebaillyapp.ultrasonicfsk.data.service.fsk

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ## SimpleBinaryParser
 *
 * Parseur binaire minimaliste pour reconstruire des messages ASCII à partir d’un flux de bits.
 *
 * Ce parser :
 * - Détecte les bits spéciaux de **début** (`startBit`, par défaut 2) et **fin** (`stopBit`, par défaut 3),
 * - Accumule les bits entre ces marqueurs,
 * - Reconstitue le message ASCII (8 bits = 1 caractère),
 * - Émet le message via un `SharedFlow`.
 *
 * ### Usage :
 * Appelle [onBitReceived] à chaque bit reçu par le `FskDecoder`.
 *
 * ---
 *
 * @param startBit Valeur spéciale indiquant le début d’un message (par défaut `2`)
 * @param stopBit Valeur spéciale indiquant la fin d’un message (par défaut `3`)
 */
class SimpleBinaryParser(
    private val startBit: Int = 2,
    private val stopBit: Int = 3
) {

    private val _messageFlow = MutableSharedFlow<String>()

    /**
     * Flux exposé pour observer les messages décodés.
     */
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    private val bitBuffer = mutableListOf<Int>()
    private var isRecording = false

    /**
     * Appelé à chaque réception d’un bit (via le `FskDecoder`).
     *
     * Gère automatiquement :
     * - Le déclenchement de la capture via le bit de start,
     * - L’arrêt via le bit de stop,
     * - La conversion des bits capturés en texte ASCII,
     * - L’émission dans [messageFlow].
     *
     * @param bit Le bit reçu : 0, 1, ou marqueur (2=start, 3=stop)
     */
    suspend fun onBitReceived(bit: Int) {
        when {
            bit == startBit && !isRecording -> {
                // Démarrage d’un nouveau message
                bitBuffer.clear()
                isRecording = true
            }
            bit == stopBit && isRecording -> {
                // Fin de message, décodage ASCII
                val msg = bitsToAscii(bitBuffer)
                _messageFlow.emit(msg)
                isRecording = false
                bitBuffer.clear()
            }
            isRecording -> {
                // Accumulation des bits utiles (0 ou 1)
                bitBuffer.add(bit)
            }
            else -> {
                // Bit ignoré (hors message)
            }
        }
    }

    /**
     * Convertit une liste de bits (par paquets de 8) en chaîne de caractères ASCII.
     *
     * ### Exemple :
     * `listOf(0,1,0,0,1,0,0,0)` → `"H"` (char = 72)
     *
     * @param bits Liste de bits (multiples de 8 recommandés)
     * @return Chaîne ASCII correspondante
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