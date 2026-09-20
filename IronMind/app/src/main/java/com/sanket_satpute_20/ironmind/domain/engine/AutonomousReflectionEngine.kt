package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * Autonomous Reflection Engine
 * 
 * Responsible for processing reflections through the AI and automatically 
 * syncing or staging extracted candidates based on user Autonomy settings.
 */
interface AutonomousReflectionEngine {
    /**
     * Processes a single reflection asynchronously.
     * 
     * Takes the reflection content, generates candidates via AI, applies 
     * the autonomy policy, and updates memory/patterns/events without 
     * modifying the original reflection.
     * 
     * @param reflectionId The ID of the reflection to process.
     * @return Result.Success on successful processing, or Result.Failure on error.
     */
    suspend fun processReflection(reflectionId: String): Result<Unit, Exception>
}
