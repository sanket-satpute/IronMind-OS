package com.sanket_satpute_20.ironmind.domain.provider

import com.sanket_satpute_20.ironmind.domain.model.SpeechInput
import kotlinx.coroutines.flow.StateFlow

interface SpeechToTextProvider {
    val speechFlow: StateFlow<SpeechInput>
    
    fun startListening()
    fun stopListening()
}
