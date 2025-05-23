package com.lebaillyapp.ultrasonicfsk.data.repository

import com.lebaillyapp.ultrasonicfsk.data.service.FskEncoder

/**
 * Repository qui encapsule la logique d'encodage FSK.
 * Peut être utilisé par un ViewModel ou une autre couche de l'app.
 */
class FskRepository(
    private val encoder: FskEncoder = FskEncoder()
) {
    /**
     * Encode un message texte en une séquence de fréquences FSK.
     */
    fun encodeMessage(message: String): List<Pair<Double, Long>> {
        return encoder.encode(message)
    }
}