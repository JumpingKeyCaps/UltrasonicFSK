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

    /**
     * Appelé depuis la UI pour encoder un message à envoyer.
     */
    fun prepareMessage(message: String) {
        val encoded = repository.encodeMessage(message)
        _frequencies.value = encoded
    }
}