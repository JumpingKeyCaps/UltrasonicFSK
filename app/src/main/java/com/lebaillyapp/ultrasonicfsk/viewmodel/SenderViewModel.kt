package com.lebaillyapp.ultrasonicfsk.viewmodel

import androidx.lifecycle.ViewModel
import com.lebaillyapp.ultrasonicfsk.data.repository.FskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SenderViewModel(
    private val repository: FskRepository = FskRepository()
) : ViewModel() {

    private val _frequencies = MutableStateFlow<List<Pair<Double, Long>>>(emptyList())
    val frequencies = _frequencies.asStateFlow()

    private val _use4Fsk = MutableStateFlow(false)
    val use4Fsk = _use4Fsk.asStateFlow()

    /**
     * Active ou désactive l'utilisation du mode 4-FSK.
     */
    fun setUse4Fsk(enabled: Boolean) {
        _use4Fsk.value = enabled
        repository.setUse4Fsk(enabled)
    }

    /**
     * Prépare le message en encodant selon le mode sélectionné (2-FSK ou 4-FSK).
     */
    fun prepareMessage(message: String) {
        val encoded = repository.encodeMessage(message)
        _frequencies.value = encoded
    }
}