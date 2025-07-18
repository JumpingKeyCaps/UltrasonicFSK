package com.lebaillyapp.ultrasonicfsk.data.repository

import android.content.Context
import com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk2.FskDecoder
import com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk2.FskEncoder
import com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk2.SimpleBinaryParser
import com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk4.FourFskParser
import com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk4.Fsk4Decoder
import com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk4.Fsk4Encoder

/**
 * Repository encapsulant la logique d'encodage et décodage FSK.
 *
 * Permet d'utiliser soit un encodeur 2-FSK soit un encodeur 4-FSK
 * pour la transmission, ainsi qu'un décodeur 2-FSK ou 4-FSK pour la réception.
 *
 * ---
 * @param use4Fsk Flag indiquant si on utilise 4-FSK (true) ou 2-FSK (false)
 */
class FskRepository(
    private var use4Fsk: Boolean = false,
) {

    private val encoder2Fsk = FskEncoder()
    private val encoder4Fsk = Fsk4Encoder()

    private var decoder2Fsk: FskDecoder? = null
    private var decoder4Fsk: Fsk4Decoder? = null

    /**
     * Initialise le décodeur avec un contexte Android, et le bon parser.
     */
    fun initDecoder(context: Context) {
        if (use4Fsk) {
            if (decoder4Fsk == null) {
                val parser = FourFskParser(startSymbol = 4, stopSymbol = 5)
                decoder4Fsk = Fsk4Decoder(context, parser)
            }
        } else {
            if (decoder2Fsk == null) {
                val parser = SimpleBinaryParser(startBit = 2, stopBit = 3)
                decoder2Fsk = FskDecoder(context, parser)
            }
        }
    }

    /**
     * Active ou désactive l’utilisation du mode 4-FSK.
     */
    fun setUse4Fsk(enabled: Boolean) {
        if (use4Fsk != enabled) {
            stopDecoding() // stop l’ancien décodeur
            use4Fsk = enabled
        }
    }

    /**
     * Encode un message selon l’encodeur actif.
     */
    fun encodeMessage(message: String): List<Pair<Double, Long>> =
        if (use4Fsk) encoder4Fsk.encode(message) else encoder2Fsk.encode(message)

    /**
     * Démarre l’écoute micro et le décodage selon le mode.
     */
    fun startDecoding() {
        if (use4Fsk) {
            decoder4Fsk?.startDecoding()
        } else {
            decoder2Fsk?.startDecoding()
        }
    }

    /**
     * Stoppe proprement l’écoute micro et le décodage.
     */
    fun stopDecoding() {
        decoder4Fsk?.stopDecoding()
        decoder2Fsk?.stopDecoding()
    }
}
