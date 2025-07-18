package com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk4

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ## FourFskParser
 *
 * Parseur pour décoder un flux de symboles 4-FSK (4 niveaux),
 * reconstruisant des messages ASCII à partir d’un flux de symboles 2 bits chacun.
 *
 * Ce parser :
 * - Détecte les symboles spéciaux de **début** (`startSymbol`, par défaut 4) et **fin** (`stopSymbol`, par défaut 5),
 * - Accumule les symboles entre ces marqueurs,
 * - Convertit chaque symbole (0..3) en 2 bits,
 * - Reconstitue le message ASCII (8 bits = 1 caractère),
 * - Émet le message via un `SharedFlow`.
 *
 * ### Usage :
 * Appelle [onSymbolReceived] à chaque symbole reçu par le `FskDecoder`.
 *
 * ---
 *
 * @param startSymbol Valeur spéciale indiquant le début d’un message (par défaut `4`)
 * @param stopSymbol Valeur spéciale indiquant la fin d’un message (par défaut `5`)
 */
class FourFskParser(
    private val startSymbol: Int = 4,
    private val stopSymbol: Int = 5
) {

    private val _messageFlow = MutableSharedFlow<String>()

    /**
     * Flux exposé pour observer les messages décodés.
     */
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    private val symbolBuffer = mutableListOf<Int>()
    private var isRecording = false

    /**
     * Appelé à chaque réception d’un symbole (via le `FskDecoder`).
     *
     * Gère automatiquement :
     * - Le déclenchement de la capture via le symbole de start,
     * - L’arrêt via le symbole de stop,
     * - La conversion des symboles capturés en texte ASCII,
     * - L’émission dans [messageFlow].
     *
     * @param symbol Le symbole reçu : 0, 1, 2, 3 ou marqueur (4=start, 5=stop)
     */
    suspend fun onSymbolReceived(symbol: Int) {
        when {
            symbol == startSymbol && !isRecording -> {
                // Démarrage d’un nouveau message
                symbolBuffer.clear()
                isRecording = true
            }
            symbol == stopSymbol && isRecording -> {
                // Fin de message, décodage ASCII
                val bits = symbolsToBits(symbolBuffer)
                val msg = bitsToAscii(bits)
                _messageFlow.emit(msg)
                isRecording = false
                symbolBuffer.clear()
            }
            isRecording -> {
                // Accumulation des symboles utiles (0 à 3)
                if (symbol in 0..3) symbolBuffer.add(symbol)
            }
            else -> {
                // Symbole ignoré (hors message)
            }
        }
    }

    /**
     * Convertit une liste de symboles 4-FSK (0..3) en bits (2 bits par symbole).
     *
     * Exemple :
     * `[2, 1]` → `[1, 0, 0, 1]` (2 = "10", 1 = "01")
     *
     * @param symbols Liste de symboles 4-FSK
     * @return Liste de bits 0/1
     */
    private fun symbolsToBits(symbols: List<Int>): List<Int> {
        val bits = mutableListOf<Int>()
        for (symbol in symbols) {
            // 2 bits par symbole, MSB first
            bits.add((symbol shr 1) and 1)
            bits.add(symbol and 1)
        }
        return bits
    }

    /**
     * Convertit une liste de bits (par paquets de 8) en chaîne de caractères ASCII.
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