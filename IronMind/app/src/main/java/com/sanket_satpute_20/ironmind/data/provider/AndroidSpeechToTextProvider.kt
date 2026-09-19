package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.SpeechInput
import com.sanket_satpute_20.ironmind.domain.provider.SpeechToTextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidSpeechToTextProvider(
    private val context: Context,
    private val logger: IronLogger
) : SpeechToTextProvider {

    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _speechFlow = MutableStateFlow(SpeechInput("", false))
    override val speechFlow: StateFlow<SpeechInput> = _speechFlow.asStateFlow()

    private var isListening = false

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        logger.logLifecycle("SpeechToText", "READY_FOR_SPEECH")
                    }

                    override fun onBeginningOfSpeech() {
                        logger.logLifecycle("SpeechToText", "BEGINNING_OF_SPEECH")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        logger.logLifecycle("SpeechToText", "END_OF_SPEECH")
                    }

                    override fun onError(error: Int) {
                        val errorMessage = getErrorText(error)
                        logger.logLifecycle("SpeechToText", "ERROR", mapOf("message" to errorMessage))
                        _speechFlow.update { it.copy(error = errorMessage, isFinal = true) }
                        isListening = false
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        logger.logLifecycle("SpeechToText", "RESULTS", mapOf("success" to true))
                        _speechFlow.update { it.copy(text = text, isFinal = true, error = null) }
                        isListening = false
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        _speechFlow.update { it.copy(text = text, isFinal = false, error = null) }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            logger.logLifecycle("SpeechToText", "INITIALIZED")
        } else {
            logger.logLifecycle("SpeechToText", "ERROR", mapOf("message" to "SpeechRecognition not available on this device"))
        }
    }

    override fun startListening() {
        if (speechRecognizer == null) {
            initializeRecognizer()
        }

        if (isListening) return
        
        _speechFlow.value = SpeechInput("", false)
        isListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            logger.logLifecycle("SpeechToText", "LISTENING_STARTED")
        } catch (e: Exception) {
            logger.logLifecycle("SpeechToText", "ERROR", mapOf("message" to e.message))
            _speechFlow.update { it.copy(error = e.message, isFinal = true) }
            isListening = false
        }
    }

    override fun stopListening() {
        if (!isListening) return
        speechRecognizer?.stopListening()
        logger.logLifecycle("SpeechToText", "LISTENING_STOPPED")
        isListening = false
    }

    private fun getErrorText(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
        SpeechRecognizer.ERROR_SERVER -> "Error from server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Didn't understand, please try again."
    }
}
