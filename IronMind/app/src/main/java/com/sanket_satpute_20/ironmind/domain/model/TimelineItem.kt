package com.sanket_satpute_20.ironmind.domain.model

sealed class TimelineItem {
    abstract val timestamp: Long
    abstract val id: String

    data class CommitmentEvent(
        override val id: String,
        override val timestamp: Long,
        val commitmentTitle: String,
        val actionDescription: String
    ) : TimelineItem()

    data class ReflectionRecorded(
        override val id: String,
        override val timestamp: Long,
        val sentiment: String
    ) : TimelineItem()

    data class GoalEvent(
        override val id: String,
        override val timestamp: Long,
        val goalTitle: String,
        val actionDescription: String
    ) : TimelineItem()

    data class PlanEvent(
        override val id: String,
        override val timestamp: Long,
        val planTitle: String,
        val actionDescription: String
    ) : TimelineItem()

    data class TaskEvent(
        override val id: String,
        override val timestamp: Long,
        val taskTitle: String,
        val actionDescription: String
    ) : TimelineItem()

    data class OutcomeRecorded(
        override val id: String,
        override val timestamp: Long,
        val actionDescription: String
    ) : TimelineItem()

    data class MajorEvent(
        override val id: String,
        override val timestamp: Long,
        val description: String
    ) : TimelineItem()
}
