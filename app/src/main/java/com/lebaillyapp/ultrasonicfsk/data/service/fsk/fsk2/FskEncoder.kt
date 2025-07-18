package com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk2

/**
 * ##  FskEncoder – Encodeur FSK (Binary Frequency-Shift Keying)
 *
 * Service chargé de convertir un message texte en une séquence de fréquences sonores
 * selon le schéma de modulation FSK binaire (2 fréquences pour 0 et 1).
 *
 * ---
 * ###  Paramètres de configuration :
 * @param freq0 Fréquence représentant le bit `0` (par défaut : 18500 Hz)
 * @param freq1 Fréquence représentant le bit `1` (par défaut : 18700 Hz)
 * @param bitDurationMs Durée d’émission d’un bit en millisecondes (par défaut : 100 ms)
 *
 * ---
 * ###  Objectif :
 * Générer une séquence de paires `(fréquence, durée)` correspondant à la version FSK d’un message texte.
 *
 * ---
 * ###  Exemple :
 * Message "A" → ASCII `01000001` → Fréquences `[freq0, freq1, freq0, ...]`
 *
 * ---
 * @author Sam
 */
class FskEncoder(
    private val freq0: Double = 18500.0,
    private val freq1: Double = 18700.0,
    private val bitDurationMs: Long = 100L
) {

    /**
     * ##  encode
     *
     * Encode un message ASCII en séquence FSK.
     *
     * ---
     * @param message Le message texte à encoder.
     * @return Une liste de paires `(fréquence, durée en ms)` correspondant à chaque bit du message.
     *
     * ---
     * ###  Note :
     * Le message est converti en binaire (8 bits par caractère ASCII),
     * puis chaque bit est transformé en fréquence selon :
     * - `0` → [freq0]
     * - `1` → [freq1]
     */
    fun encode(message: String): List<Pair<Double, Long>> {
        val binary = messageToBinary(message)
        return binary.map { bit ->
            val freq = if (bit == '0') freq0 else freq1
            freq to bitDurationMs
        }
    }

    /**
     * ##  messageToBinary
     *
     * Convertit un message texte ASCII en représentation binaire brute (sans séparation).
     *
     * ---
     * @param message Le texte à convertir (ex: "Hi")
     * @return Une chaîne binaire (ex: `"0100100001101001"`)
     *
     * ---
     * ###  Détail :
     * Chaque caractère ASCII est encodé sur 8 bits.
     */
    private fun messageToBinary(message: String): String {
        return message.toCharArray().joinToString(separator = "") { char ->
            char.code.toString(2).padStart(8, '0') // 8 bits par caractère
        }
    }
}