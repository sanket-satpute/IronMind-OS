package com.sanket_satpute_20.ironmind.domain.model

data class SpeechInput(
    val text: String,
    val isFinal: Boolean,
    val error: String? = null
)
