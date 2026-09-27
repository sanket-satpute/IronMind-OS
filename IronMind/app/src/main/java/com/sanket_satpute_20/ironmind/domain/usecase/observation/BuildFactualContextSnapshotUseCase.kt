package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot

/**
 * Deterministically assembles a cross-source [FactualContextSnapshot] over a specified time window.
 * 
 * Invokes the existing strongly-typed observation aggregators exactly.
 * Any single source failure aborts the entire snapshot build to prevent "missing" data
 * from being conflated with "successful but empty" data.
 */
class BuildFactualContextSnapshotUseCase(
    private val aggregateAppUsage: AggregateAppUsageObservationsUseCase,
    private val aggregateActivity: AggregateActivityObservationsUseCase,
    private val aggregateCalendar: AggregateCalendarObservationsUseCase,
    private val aggregateLocation: AggregateLocationObservationsUseCase,
    private val aggregateNotifications: AggregateNotificationObservationsUseCase
) {
    suspend operator fun invoke(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<FactualContextSnapshot, Exception> {
        if (startTimeMs >= endTimeMs) {
            return Result.Failure(IllegalArgumentException("startTimeMs must be less than endTimeMs"))
        }

        val appUsageResult = aggregateAppUsage(userId, startTimeMs, endTimeMs)
        if (appUsageResult is Result.Failure) {
            println("IronMindLifecycle [FactualContext] [SNAPSHOT_FAILED] userId=$userId error=${appUsageResult.error.message}")
            return Result.Failure(appUsageResult.error)
        }

        val activityResult = aggregateActivity(userId, startTimeMs, endTimeMs)
        if (activityResult is Result.Failure) {
            println("IronMindLifecycle [FactualContext] [SNAPSHOT_FAILED] userId=$userId error=${activityResult.error.message}")
            return Result.Failure(activityResult.error)
        }

        val calendarResult = aggregateCalendar(userId, startTimeMs, endTimeMs)
        if (calendarResult is Result.Failure) {
            println("IronMindLifecycle [FactualContext] [SNAPSHOT_FAILED] userId=$userId error=${calendarResult.error.message}")
            return Result.Failure(calendarResult.error)
        }

        val locationResult = aggregateLocation(userId, startTimeMs, endTimeMs)
        if (locationResult is Result.Failure) {
            println("IronMindLifecycle [FactualContext] [SNAPSHOT_FAILED] userId=$userId error=${locationResult.error.message}")
            return Result.Failure(locationResult.error)
        }

        val notificationsResult = aggregateNotifications(userId, startTimeMs, endTimeMs)
        if (notificationsResult is Result.Failure) {
            println("IronMindLifecycle [FactualContext] [SNAPSHOT_FAILED] userId=$userId error=${notificationsResult.error.message}")
            return Result.Failure(notificationsResult.error)
        }

        println("IronMindLifecycle [FactualContext] [SNAPSHOT_BUILT] userId=$userId startTimeMs=$startTimeMs endTimeMs=$endTimeMs")

        return Result.Success(
            FactualContextSnapshot(
                userId = userId,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                appUsage = (appUsageResult as Result.Success).data,
                activity = (activityResult as Result.Success).data,
                calendar = (calendarResult as Result.Success).data,
                location = (locationResult as Result.Success).data,
                notifications = (notificationsResult as Result.Success).data
            )
        )
    }
}
