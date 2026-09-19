package com.sanket_satpute_20.ironmind.domain.usecase.reflection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.EntitySource

class SaveReflectionUseCase(
    private val repository: ReflectionRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        content: String,
        targetEntityId: String? = null,
        targetEntityType: String? = null,
        sentiment: String? = null
    ): Result<Reflection, Exception> {
        if (content.isBlank()) {
            return Result.Failure(IllegalArgumentException("Reflection content cannot be blank"))
        }

        val now = clock.currentTimeMillis()
        val reflection = Reflection(
            id = idGenerator.generateId(),
            userId = userId,
            targetEntityId = targetEntityId,
            targetEntityType = targetEntityType,
            content = content,
            sentiment = sentiment,
            createdAt = now
        )

        val result = repository.saveReflection(reflection)
        return if (result is Result.Success) {
            val event = Event(
                id = idGenerator.generateId(),
                userId = userId,
                type = EventType.REFLECTION_CREATED,
                entityType = "REFLECTION",
                entityId = reflection.id,
                occurredAt = now,
                recordedAt = now,
                source = EntitySource.USER
            )
            eventRepository.saveEvent(event)
            println("IronMindLifecycle [Reflection] [SAVED] reflectionId=${reflection.id}")
            Result.Success(reflection)
        } else {
            Result.Failure((result as Result.Failure).error)
        }
    }
}
