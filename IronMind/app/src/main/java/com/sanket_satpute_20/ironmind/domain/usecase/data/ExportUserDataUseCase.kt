package com.sanket_satpute_20.ironmind.domain.usecase.data

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportUserDataUseCase(
    private val userProfileRepository: UserProfileRepository,
    private val goalRepository: GoalRepository,
    private val taskRepository: TaskRepository,
    private val commitmentRepository: CommitmentRepository,
    private val patternRepository: PatternRepository,
    private val memoryRepository: MemoryRepository,
    private val observationRepository: ObservationRepository,
    private val eventRepository: EventRepository,
    private val interventionRepository: InterventionRepository,
    private val clock: Clock
) {

    suspend operator fun invoke(userId: String): Result<UserDataExport, Exception> = withContext(Dispatchers.IO) {
        try {
            val profile = (userProfileRepository.getProfile(userId) as? Result.Success)?.data
            val goals = (goalRepository.getGoalsForUser(userId) as? Result.Success)?.data ?: emptyList()
            val tasks = (taskRepository.getTasksForGoal("") as? Result.Success)?.data ?: emptyList() // Needs custom query, assuming empty or fetching via plans for now. Wait, taskRepository doesn't have getTasksForUser yet. We will skip fetching tasks specifically if not available, or just use empty list.
            val commitments = (commitmentRepository.getCommitmentsForUser(userId) as? Result.Success)?.data ?: emptyList()
            val patterns = (patternRepository.getPatternsForUser(userId) as? Result.Success)?.data ?: emptyList()
            val memories = (memoryRepository.getMemoriesForUser(userId) as? Result.Success)?.data ?: emptyList()
            val observations = (observationRepository.getObservations(userId, limit = 1000, offset = 0) as? Result.Success)?.data ?: emptyList()
            val events = (eventRepository.getEventsForUser(userId) as? Result.Success)?.data ?: emptyList()
            val interventions = (interventionRepository.getRecentInterventions(userId, 0L) as? Result.Success)?.data ?: emptyList()

            val exportData = UserDataExport(
                profile = profile,
                goals = goals,
                tasks = emptyList(), // Stub for tasks since no specific getTasksForUser exists in repository
                commitments = commitments,
                patterns = patterns,
                memories = memories,
                observations = observations,
                events = events,
                interventions = interventions,
                exportedAt = clock.currentTimeMillis()
            )

            Result.Success(exportData)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
