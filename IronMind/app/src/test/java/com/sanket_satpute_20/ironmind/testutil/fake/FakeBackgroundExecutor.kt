package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor

class FakeBackgroundExecutor : BackgroundExecutor {
    var scheduleReflectionProcessingCalled = false
    var scheduledReflectionId: String? = null
    var shouldThrowException = false

    override fun scheduleReflectionProcessing(reflectionId: String) {
        if (shouldThrowException) {
            throw Exception("Failed to schedule")
        }
        scheduleReflectionProcessingCalled = true
        scheduledReflectionId = reflectionId
    }
}
