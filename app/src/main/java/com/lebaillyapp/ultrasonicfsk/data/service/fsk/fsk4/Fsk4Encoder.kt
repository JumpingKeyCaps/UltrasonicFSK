package com.lebaillyapp.ultrasonicfsk.data.service.fsk.fsk4

/**
 * ## Fsk4Encoder – Encodeur 4-FSK (Frequency Shift Keying à 4 niveaux)
 *
 * Service chargé de convertir un message texte en une séquence de fréquences sonores
 * selon un schéma de modulation FSK à 4 niveaux, où chaque symbole encode 2 bits.
 *
 * ---
 * ### Paramètres de configuration :
 * @param freqBase Fréquence de base correspondant au symbole `00` (par défaut : 18100 Hz)
 * @param freqStep Espacement en Hz entre chaque symbole consécutif (par défaut : 200 Hz)
 * @param bitDurationMs Durée d’émission d’un symbole en millisecondes (par défaut : 100 ms)
 *
 * ---
 * ### Objectif :
 * Générer une séquence de paires `(fréquence, durée)` correspondant à la version 4-FSK
 * d’un message texte ASCII.
 *
 * ---
 * ### Exemple :
 * Message "A" → ASCII `01000001` → symboles `01 00 00 01` → fréquences `[freqBase+freqStep, freqBase, freqBase, freqBase+freqStep]`
 *
 * ---
 * @author Sam
 */
class Fsk4Encoder(
    private val freqBase: Double = 18100.0,
    private val freqStep: Double = 200.0,
    private val bitDurationMs: Long = 100L
) {

    private val symbolMap = mapOf(
        "00" to freqBase,
        "01" to freqBase + freqStep,
        "10" to freqBase + 2 * freqStep,
        "11" to freqBase + 3 * freqStep
    )

    /**
     * ## encode
     *
     * Encode un message ASCII en séquence 4-FSK.
     *
     * ---
     * @param message Le message texte à encoder.
     * @return Une liste de paires `(fréquence, durée en ms)` correspondant à chaque symbole 2 bits.
     *
     * ---
     * ### Note :
     * Le message est converti en binaire (8 bits par caractère ASCII),
     * puis les bits sont regroupés par paires de 2 (ajout d’un 0 si impair).
     * Chaque paire est mappée vers une fréquence correspondante dans `symbolMap`.
     */
    fun encode(message: String): List<Pair<Double, Long>> {
        val binary = message.toCharArray()
            .joinToString(separator = "") { char -> char.code.toString(2).padStart(8, '0') }

        val bitPairs = binary.chunked(2).map {
            if (it.length == 1) it + "0" else it // Padding si dernier symbole incomplet
        }

        return bitPairs.mapNotNull { pair ->
            symbolMap[pair]?.let { freq -> freq to bitDurationMs }
        }
    }
}
