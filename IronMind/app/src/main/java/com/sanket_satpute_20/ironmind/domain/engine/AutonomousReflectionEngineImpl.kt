package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository

class AutonomousReflectionEngineImpl(
    private val reflectionRepository: ReflectionRepository,
    private val ironMindAI: IronMindAI,
    private val autonomySettingsRepository: AutonomySettingsRepository,
    private val memoryRepository: MemoryRepository,
    private val patternRepository: PatternRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) : AutonomousReflectionEngine {

    override suspend fun processReflection(reflectionId: String): Result<Unit, Exception> {
        val reflectionResult = reflectionRepository.getReflection(reflectionId)
        if (reflectionResult is Result.Failure) {
            return Result.Failure(reflectionResult.error)
        }
        
        val reflection = (reflectionResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Reflection not found"))

        // Obtain autonomy settings for the user
        val settingsResult = autonomySettingsRepository.getSettings(reflection.userId)
        if (settingsResult is Result.Failure) {
            return Result.Failure(settingsResult.error)
        }
        val settings = (settingsResult as Result.Success).data
        
        val reflectionProcessingLevel = settings.getLevel(AutonomyCapability.REFLECTION_PROCESSING)
        val memoryPatternProcessingLevel = settings.getLevel(AutonomyCapability.MEMORY_PATTERN_PROCESSING)

        // If OFF, we do not autonomously process this reflection
        if (reflectionProcessingLevel == AutonomyLevel.OFF) {
            println("IronMindLifecycle [Reflection] [SKIPPED_POLICY_OFF] reflectionId=$reflectionId")
            return Result.Success(Unit)
        }

        // Process through AI
        val request = AIRequest(
            userId = reflection.userId,
            input = reflection.content,
            requestType = AIRequestType.REFLECTION_UNDERSTANDING,
            contextSnapshot = null
        )

        val aiResult = ironMindAI.process(request)
        if (aiResult is Result.Failure) {
            return Result.Failure(aiResult.error)
        }

        val aiOutput = (aiResult as Result.Success).data
        val now = clock.currentTimeMillis()

        // Handle candidates based on autonomy policy
        when (aiOutput) {
            is AIOutput.MemoryCandidate -> {
                if (memoryPatternProcessingLevel == AutonomyLevel.FULL_AUTO || memoryPatternProcessingLevel == AutonomyLevel.ASK_BEFORE_ACTION) {
                    val confirmationState = if (memoryPatternProcessingLevel == AutonomyLevel.FULL_AUTO) {
                        MemoryConfirmationState.SYSTEM_CONFIRMED
                    } else {
                        MemoryConfirmationState.UNCONFIRMED
                    }
                    
                    val memory = Memory(
                        id = idGenerator.generateId(),
                        userId = reflection.userId,
                        type = "inference",
                        content = aiOutput.candidateContent,
                        source = EntitySource.SYSTEM,
                        confidence = aiOutput.confidence,
                        evidenceCount = 1,
                        firstObservedAt = now,
                        lastObservedAt = now,
                        confirmationState = confirmationState,
                        status = MemoryStatus.ACTIVE,
                        expiresAt = null,
                        createdAt = now,
                        updatedAt = now
                    )
                    memoryRepository.saveMemory(memory)
                }
            }
            is AIOutput.PatternCandidate -> {
                if (memoryPatternProcessingLevel == AutonomyLevel.FULL_AUTO || memoryPatternProcessingLevel == AutonomyLevel.ASK_BEFORE_ACTION) {
                    val confirmationState = if (memoryPatternProcessingLevel == AutonomyLevel.FULL_AUTO) {
                        MemoryConfirmationState.SYSTEM_CONFIRMED
                    } else {
                        MemoryConfirmationState.UNCONFIRMED
                    }
                    
                    val pattern = Pattern(
                        id = idGenerator.generateId(),
                        userId = reflection.userId,
                        type = PatternType.CONTEXT_PATTERN,
                        description = aiOutput.patternDescription,
                        conditions = null,
                        predictedBehavior = null,
                        confidence = aiOutput.confidence,
                        evidenceCount = 1, // First observation
                        evidenceReferences = aiOutput.evidenceReferences,
                        firstObservedAt = now,
                        lastObservedAt = now,
                        status = PatternStatus.ACTIVE,
                        confirmationState = confirmationState,
                        createdAt = now,
                        updatedAt = now
                    )
                    patternRepository.savePattern(pattern)
                }
            }
            else -> {
                // If it's ReflectionExtraction, NoAction, or ClarificationRequest, 
                // we don't automatically generate domain state without further logic,
                // preserving exactly the "memory/pattern updated" mandate.
            }
        }

        println("IronMindLifecycle [Reflection] [PROCESSED] reflectionId=$reflectionId")
        return Result.Success(Unit)
    }
}
