package com.lebaillyapp.ultrasonicfsk.data.repository

import android.content.Context
import com.lebaillyapp.ultrasonicfsk.data.service.FskDecoder
import com.lebaillyapp.ultrasonicfsk.data.service.FskEncoder
import kotlinx.coroutines.flow.SharedFlow

/**
 * Repository qui encapsule la logique d'encodage/decodage FSK.
 * Peut être utilisé par un ViewModel ou une autre couche de l'app.
 */
class FskRepository(
    private val encoder: FskEncoder = FskEncoder(),
    private var decoder: FskDecoder? = null
) {

    /**
     * Initialise le décodeur avec un contexte si non déjà initialisé.
     */
    fun initDecoder(context: Context) {
        if (decoder == null) decoder = FskDecoder(context)
    }

    fun encodeMessage(message: String): List<Pair<Double, Long>> {
        return encoder.encode(message)
    }

    val bitFlow: SharedFlow<Int>?
        get() = decoder?.bitFlow

    fun startDecoding() {
        decoder?.startDecoding()
    }

    fun stopDecoding() {
        decoder?.stopDecoding()
    }
}