package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.Task
import kotlinx.coroutines.CancellationException

interface TaskRepository : Repository {
    suspend fun saveTask(task: Task): Result<Unit, Exception>
    suspend fun getTask(id: String): Result<Task?, Exception>
    suspend fun getTasksForPlan(planId: String): Result<List<Task>, Exception>
    suspend fun getTasksForGoal(goalId: String): Result<List<Task>, Exception>
    suspend fun deleteTask(userId: String, taskId: String): Result<Unit, Exception>
    suspend fun getTasksForUser(userId: String): Result<List<Task>, Exception> = Result.Success(emptyList())

    suspend fun saveTaskWithEvent(
        task: Task,
        previousTask: Task,
        event: Event,
        eventRepository: EventRepository
    ): Result<Unit, Exception> {
        val saveResult = saveTask(task)
        if (saveResult is Result.Failure) {
            return saveResult
        }

        val eventResult = try {
            eventRepository.saveEvent(event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }

        if (eventResult is Result.Failure) {
            saveTask(previousTask)
            return Result.Failure(eventResult.error)
        }

        return Result.Success(Unit)
    }
}
