package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class ContextEngineImpl(
    private val clock: Clock,
    private val commitmentRepository: CommitmentRepository,
    private val eventRepository: EventRepository,
    private val observationRepository: ObservationRepository,
    private val reflectionRepository: ReflectionRepository,
    private val protectionRepository: ProtectionRepository,
    private val goalRepository: GoalRepository
) : ContextEngine {

    override suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception> = withContext(Dispatchers.IO) {
        try {
            val now = clock.currentTimeMillis()
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // 1. Fetch active commitments for today
            val commitmentsResult = commitmentRepository.getCommitmentsForUser(userId)
            val activeCommitments = if (commitmentsResult is Result.Success) {
                commitmentsResult.data.filter { it.status.name == "ACTIVE" } // Approximate, assuming CommitmentStatus has name
            } else {
                emptyList()
            }

            // 2. Fetch recent events
            val eventsResult = eventRepository.getEventsForUser(userId)
            val recentEvents = if (eventsResult is Result.Success) {
                eventsResult.data.sortedByDescending { it.occurredAt }.take(10)
            } else {
                emptyList()
            }

            // 3. Fetch recent observations
            val observationsResult = observationRepository.getObservations(userId, limit = 10, offset = 0)
            val recentObservations = if (observationsResult is Result.Success) observationsResult.data else emptyList()

            // 4. Fetch recent reflections
            // Using past 7 days roughly
            val startTime = now - (7 * 24 * 60 * 60 * 1000L)
            val reflectionsResult = reflectionRepository.getReflectionsForDateRange(userId, startTime, now)
            val recentReflections = if (reflectionsResult is Result.Success) {
                reflectionsResult.data.sortedByDescending { it.createdAt }.take(3)
            } else {
                emptyList()
            }

            // 5. Fetch active protection session
            val activeSessionResult = protectionRepository.getActiveProtectionSessionsForUser(userId)
            val activeProtectionSession = if (activeSessionResult is Result.Success) activeSessionResult.data.firstOrNull() else null

            // 6. Fetch active goals
            val goalsResult = goalRepository.getGoalsForUser(userId)
            val activeGoals = if (goalsResult is Result.Success) {
                goalsResult.data.filter { it.status.name == "ACTIVE" } // Assuming GoalStatus has name
            } else {
                emptyList()
            }

            val snapshot = ContextSnapshot(
                timestamp = now,
                dayOfWeek = dayOfWeek,
                activeCommitments = activeCommitments,
                recentEvents = recentEvents,
                recentObservations = recentObservations,
                recentReflections = recentReflections,
                activeProtectionSession = activeProtectionSession,
                activeGoals = activeGoals
            )

            println("IronMindLifecycle [ContextEngine] [SNAPSHOT_GENERATED] userId=\$userId timestamp=\$now")
            Result.Success(snapshot)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
