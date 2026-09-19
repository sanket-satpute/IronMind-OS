package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.model.SpeechInput
import com.sanket_satpute_20.ironmind.domain.provider.SpeechToTextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSpeechToTextProvider : SpeechToTextProvider {
    private val _speechFlow = MutableStateFlow(SpeechInput("", false))
    override val speechFlow: StateFlow<SpeechInput> = _speechFlow.asStateFlow()

    var isListening = false
        private set

    override fun startListening() {
        isListening = true
        _speechFlow.value = SpeechInput("", false)
    }

    override fun stopListening() {
        isListening = false
    }

    // Methods for tests to simulate speech inputs
    fun simulateSpeechResult(text: String, isFinal: Boolean = true) {
        if (isListening) {
            _speechFlow.value = SpeechInput(text, isFinal)
            if (isFinal) isListening = false
        }
    }

    fun simulateError(error: String) {
        if (isListening) {
            _speechFlow.value = SpeechInput("", true, error)
            isListening = false
        }
    }
}
