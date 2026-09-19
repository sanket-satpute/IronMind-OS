package com.sanket_satpute_20.ironmind.testutil.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSpeechToTextProviderTest {

    private lateinit var fakeProvider: FakeSpeechToTextProvider

    @Before
    fun setup() {
        fakeProvider = FakeSpeechToTextProvider()
    }

    @Test
    fun `startListening sets isListening to true`() {
        fakeProvider.startListening()
        assertTrue(fakeProvider.isListening)
    }

    @Test
    fun `stopListening sets isListening to false`() {
        fakeProvider.startListening()
        fakeProvider.stopListening()
        assertFalse(fakeProvider.isListening)
    }

    @Test
    fun `simulateSpeechResult emits text and updates isListening if final`() {
        fakeProvider.startListening()
        fakeProvider.simulateSpeechResult("Hello", isFinal = false)
        
        assertEquals("Hello", fakeProvider.speechFlow.value.text)
        assertFalse(fakeProvider.speechFlow.value.isFinal)
        assertTrue(fakeProvider.isListening)
        
        fakeProvider.simulateSpeechResult("Hello World", isFinal = true)
        
        assertEquals("Hello World", fakeProvider.speechFlow.value.text)
        assertTrue(fakeProvider.speechFlow.value.isFinal)
        assertFalse(fakeProvider.isListening)
    }

    @Test
    fun `simulateError emits error and stops listening`() {
        fakeProvider.startListening()
        fakeProvider.simulateError("Microphone not found")
        
        assertEquals("Microphone not found", fakeProvider.speechFlow.value.error)
        assertTrue(fakeProvider.speechFlow.value.isFinal)
        assertFalse(fakeProvider.isListening)
    }
}
