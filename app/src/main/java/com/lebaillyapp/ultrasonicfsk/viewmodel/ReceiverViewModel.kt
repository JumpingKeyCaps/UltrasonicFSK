package com.lebaillyapp.ultrasonicfsk.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.lebaillyapp.ultrasonicfsk.data.repository.FskRepository

class ReceiverViewModel(
    private val repository: FskRepository = FskRepository()
) : ViewModel() {

    fun init(context: Context) {
        repository.initDecoder(context)
    }

    fun startDecoding() {
        repository.startDecoding()
    }

    fun stopDecoding() {
        repository.stopDecoding()
    }

    // val bitFlow = repository.bitFlow // à décommenter si besoin
}