package com.sanket_satpute_20.ironmind.domain.usecase.search

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.repository.*

data class LocalSearchResult(
    val goals: List<Goal> = emptyList(),
    val commitments: List<Commitment> = emptyList(),
    val reflections: List<Reflection> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val events: List<Event> = emptyList()
)

class LocalSearchUseCase(
    private val goalRepository: GoalRepository,
    private val commitmentRepository: CommitmentRepository,
    private val reflectionRepository: ReflectionRepository,
    private val memoryRepository: MemoryRepository,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(userId: String, query: String): Result<LocalSearchResult, Exception> {
        if (query.isBlank()) {
            return Result.Success(LocalSearchResult())
        }
        
        try {
            val goals = when (val res = goalRepository.searchGoals(userId, query)) {
                is Result.Success -> res.data
                is Result.Failure -> emptyList()
            }
            
            val commitments = when (val res = commitmentRepository.searchCommitments(userId, query)) {
                is Result.Success -> res.data
                is Result.Failure -> emptyList()
            }
            
            val reflections = when (val res = reflectionRepository.searchReflections(userId, query)) {
                is Result.Success -> res.data
                is Result.Failure -> emptyList()
            }
            
            val memories = when (val res = memoryRepository.searchMemories(userId, query)) {
                is Result.Success -> res.data
                is Result.Failure -> emptyList()
            }
            
            val events = when (val res = eventRepository.searchEvents(userId, query)) {
                is Result.Success -> res.data
                is Result.Failure -> emptyList()
            }
            
            return Result.Success(
                LocalSearchResult(
                    goals = goals,
                    commitments = commitments,
                    reflections = reflections,
                    memories = memories,
                    events = events
                )
            )
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
}
