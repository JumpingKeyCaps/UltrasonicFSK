package com.lebaillyapp.ultrasonicfsk.data.service

/**
 * Service responsable d’encoder un message texte en une séquence de fréquences ultrasonores
 * selon un schéma FSK binaire (ex : 18500 Hz = 0, 18700 Hz = 1).
 */
class FskEncoder(
    private val freq0: Double = 18500.0,
    private val freq1: Double = 18700.0,
    private val bitDurationMs: Long = 100L
) {

    /**
     * Encode un message texte ASCII en une liste de fréquences correspondant
     * à chaque bit FSK du message.
     *
     * @param message Le message texte à encoder.
     * @return Une liste de paires (fréquence, durée en ms) à jouer.
     */
    fun encode(message: String): List<Pair<Double, Long>> {
        val binary = messageToBinary(message)
        return binary.map { bit ->
            val freq = if (bit == '0') freq0 else freq1
            freq to bitDurationMs
        }
    }

    /**
     * Convertit un texte ASCII en une chaîne binaire (ex: "Hi" → "0100100001101001").
     */
    private fun messageToBinary(message: String): String {
        return message.toCharArray().joinToString(separator = "") { char ->
            char.code.toString(2).padStart(8, '0') // 8 bits par caractère
        }
    }
}