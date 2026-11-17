package com.sanket_satpute_20.ironmind.data.export

import android.util.JsonWriter
import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.usecase.data.*
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

/**
 * Streaming JSON serializer for UserDataExport.
 *
 * Requirements & Epistemic Guarantees:
 * 1. True Streaming: Serializes records incrementally to the output stream without
 *    constructing a massive intermediate in-memory JSON string or tree structure.
 * 2. UTF-8 & Proper Escaping: Leverages JsonWriter to safely escape control characters,
 *    newlines, quotes, and Unicode characters.
 * 3. Exact Contract: Produces the documented root object containing format, exportVersion,
 *    exportedAt, userId, appVersion, and the 26-category data payload.
 * 4. Fidelity: Preserves nulls as JSON null, empty collections as [], and enums/booleans/numbers
 *    without semantic mutations.
 */
open class UserDataExportJsonSerializer {

    /**
     * Serializes UserDataExport directly to an OutputStream in UTF-8.
     * Note: Does not close the stream to allow caller-managed stream lifecycle,
     * but flushes all buffered output upon completion.
     */
    open fun write(
        outputStream: OutputStream,
        export: UserDataExport,
        appVersion: String? = null
    ) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        write(writer, export, appVersion)
        writer.flush()
    }

    /**
     * Serializes UserDataExport to a generic Writer using android.util.JsonWriter.
     */
    open fun write(
        writer: Writer,
        export: UserDataExport,
        appVersion: String? = null
    ) {
        val jsonWriter = JsonWriter(writer)
        jsonWriter.setIndent("  ")

        jsonWriter.beginObject() // Root {
        jsonWriter.name("format").value(UserDataExport.EXPORT_FORMAT_IDENTIFIER)
        jsonWriter.name("exportVersion").value(export.exportVersion.toLong())
        jsonWriter.name("exportedAt").value(export.exportedAt)
        jsonWriter.name("userId").value(export.userId)
        if (appVersion != null) {
            jsonWriter.name("appVersion").value(appVersion)
        } else {
            jsonWriter.name("appVersion").nullValue()
        }

        // 26 categories container: "data": { ... }
        jsonWriter.name("data")
        jsonWriter.beginObject()

        // 1. User Profile (1:1)
        jsonWriter.name("userProfile")
        if (export.profile != null) {
            writeUserProfile(jsonWriter, export.profile)
        } else {
            jsonWriter.nullValue()
        }

        // 2. Goals (1:N)
        jsonWriter.name("goals")
        jsonWriter.beginArray()
        for (goal in export.goals) {
            writeGoal(jsonWriter, goal)
        }
        jsonWriter.endArray()

        // 3. Plans (1:N)
        jsonWriter.name("plans")
        jsonWriter.beginArray()
        for (plan in export.plans) {
            writePlan(jsonWriter, plan)
        }
        jsonWriter.endArray()

        // 4. Tasks (1:N)
        jsonWriter.name("tasks")
        jsonWriter.beginArray()
        for (task in export.tasks) {
            writeTask(jsonWriter, task)
        }
        jsonWriter.endArray()

        // 5. Commitments (1:N)
        jsonWriter.name("commitments")
        jsonWriter.beginArray()
        for (commitment in export.commitments) {
            writeCommitment(jsonWriter, commitment)
        }
        jsonWriter.endArray()

        // 6. Outcomes (1:N)
        jsonWriter.name("outcomes")
        jsonWriter.beginArray()
        for (outcome in export.outcomes) {
            writeOutcome(jsonWriter, outcome)
        }
        jsonWriter.endArray()

        // 7. Reflections (1:N)
        jsonWriter.name("reflections")
        jsonWriter.beginArray()
        for (reflection in export.reflections) {
            writeReflection(jsonWriter, reflection)
        }
        jsonWriter.endArray()

        // 8. Protection Rules (1:N)
        jsonWriter.name("protectionRules")
        jsonWriter.beginArray()
        for (rule in export.protectionRules) {
            writeProtectionRule(jsonWriter, rule)
        }
        jsonWriter.endArray()

        // 9. Protection Sessions (1:N)
        jsonWriter.name("protectionSessions")
        jsonWriter.beginArray()
        for (session in export.protectionSessions) {
            writeProtectionSession(jsonWriter, session)
        }
        jsonWriter.endArray()

        // 10. Events (1:N)
        jsonWriter.name("events")
        jsonWriter.beginArray()
        for (event in export.events) {
            writeEvent(jsonWriter, event)
        }
        jsonWriter.endArray()

        // 11. Memories (1:N)
        jsonWriter.name("memories")
        jsonWriter.beginArray()
        for (memory in export.memories) {
            writeMemory(jsonWriter, memory)
        }
        jsonWriter.endArray()

        // 12. Observations (1:N)
        jsonWriter.name("observations")
        jsonWriter.beginArray()
        for (obs in export.observations) {
            writeObservation(jsonWriter, obs)
        }
        jsonWriter.endArray()

        // 13. Patterns (1:N)
        jsonWriter.name("patterns")
        jsonWriter.beginArray()
        for (pattern in export.patterns) {
            writePattern(jsonWriter, pattern)
        }
        jsonWriter.endArray()

        // 14. Autonomy Settings (1:N)
        jsonWriter.name("autonomySettings")
        jsonWriter.beginArray()
        for (setting in export.autonomySettings) {
            writeAutonomySetting(jsonWriter, setting)
        }
        jsonWriter.endArray()

        // 15. Global Autonomy State (1:1)
        jsonWriter.name("globalAutonomyState")
        if (export.globalAutonomyState != null) {
            writeGlobalAutonomyState(jsonWriter, export.globalAutonomyState)
        } else {
            jsonWriter.nullValue()
        }

        // 16. App Usage Observation Settings (1:1)
        jsonWriter.name("appUsageObservationSettings")
        if (export.appUsageObservationSettings != null) {
            writeAppUsageSettings(jsonWriter, export.appUsageObservationSettings)
        } else {
            jsonWriter.nullValue()
        }

        // 17. Intervention Records (1:N)
        jsonWriter.name("interventionRecords")
        jsonWriter.beginArray()
        for (record in export.interventionRecords) {
            writeInterventionRecord(jsonWriter, record)
        }
        jsonWriter.endArray()

        // 18. Notification Observation Settings (1:1)
        jsonWriter.name("notificationObservationSettings")
        if (export.notificationObservationSettings != null) {
            writeNotificationSettings(jsonWriter, export.notificationObservationSettings)
        } else {
            jsonWriter.nullValue()
        }

        // 19. Calendar Observation Settings (1:1)
        jsonWriter.name("calendarObservationSettings")
        if (export.calendarObservationSettings != null) {
            writeCalendarSettings(jsonWriter, export.calendarObservationSettings)
        } else {
            jsonWriter.nullValue()
        }

        // 20. Location Observation Settings (1:1)
        jsonWriter.name("locationObservationSettings")
        if (export.locationObservationSettings != null) {
            writeLocationSettings(jsonWriter, export.locationObservationSettings)
        } else {
            jsonWriter.nullValue()
        }

        // 21. Activity Observation Settings (1:1)
        jsonWriter.name("activityObservationSettings")
        if (export.activityObservationSettings != null) {
            writeActivitySettings(jsonWriter, export.activityObservationSettings)
        } else {
            jsonWriter.nullValue()
        }

        // 22. Experiment Records (1:N)
        jsonWriter.name("experimentRecords")
        jsonWriter.beginArray()
        for (exp in export.experimentRecords) {
            writeExperimentRecord(jsonWriter, exp)
        }
        jsonWriter.endArray()

        // 23. Barrier Hypotheses (1:N)
        jsonWriter.name("barrierHypotheses")
        jsonWriter.beginArray()
        for (barrier in export.barrierHypotheses) {
            writeBarrierHypothesis(jsonWriter, barrier)
        }
        jsonWriter.endArray()

        // 24. Intervention Recommendations (1:N)
        jsonWriter.name("interventionRecommendations")
        jsonWriter.beginArray()
        for (rec in export.interventionRecommendations) {
            writeInterventionRecommendation(jsonWriter, rec)
        }
        jsonWriter.endArray()

        // 25. Learner Event Statuses (1:N)
        jsonWriter.name("learnerEventStatuses")
        jsonWriter.beginArray()
        for (status in export.learnerEventStatuses) {
            writeLearnerEventStatus(jsonWriter, status)
        }
        jsonWriter.endArray()

        // 26. Correction Reviews (1:N)
        jsonWriter.name("correctionReviews")
        jsonWriter.beginArray()
        for (review in export.correctionReviews) {
            writeCorrectionReview(jsonWriter, review)
        }
        jsonWriter.endArray()

        jsonWriter.endObject() // End "data" }
        jsonWriter.endObject() // End Root }
        jsonWriter.flush()
    }

    // --- Entity Writers ---

    private fun writeUserProfile(w: JsonWriter, p: UserProfile) {
        w.beginObject()
        w.name("id").value(p.id)
        w.name("createdAt").value(p.createdAt)
        w.name("updatedAt").value(p.updatedAt)
        w.name("displayName").value(p.displayName)
        w.name("timezone").value(p.timezone)
        w.name("createdFrom").value(p.createdFrom)
        w.name("status").value(p.status)
        w.endObject()
    }

    private fun writeGoal(w: JsonWriter, g: Goal) {
        w.beginObject()
        w.name("id").value(g.id)
        w.name("userId").value(g.userId)
        writeNullableString(w, "ambitionId", g.ambitionId)
        w.name("title").value(g.title)
        w.name("description").value(g.description)
        w.name("why").value(g.why)
        w.name("importance").value(g.importance.toLong())
        w.name("status").value(g.status.name)
        writeNullableLong(w, "targetAt", g.targetAt)
        writeNullableLong(w, "startedAt", g.startedAt)
        writeNullableLong(w, "completedAt", g.completedAt)
        w.name("createdAt").value(g.createdAt)
        w.name("updatedAt").value(g.updatedAt)
        w.endObject()
    }

    private fun writePlan(w: JsonWriter, p: Plan) {
        w.beginObject()
        w.name("id").value(p.id)
        w.name("userId").value(p.userId)
        writeNullableString(w, "goalId", p.goalId)
        writeNullableString(w, "ambitionId", p.ambitionId)
        w.name("title").value(p.title)
        w.name("description").value(p.description)
        w.name("status").value(p.status.name)
        w.name("createdAt").value(p.createdAt)
        w.name("updatedAt").value(p.updatedAt)
        writeNullableLong(w, "startedAt", p.startedAt)
        writeNullableLong(w, "completedAt", p.completedAt)
        w.name("source").value(p.source.name)
        w.endObject()
    }

    private fun writeTask(w: JsonWriter, t: Task) {
        w.beginObject()
        w.name("id").value(t.id)
        w.name("userId").value(t.userId)
        writeNullableString(w, "goalId", t.goalId)
        writeNullableString(w, "planId", t.planId)
        writeNullableString(w, "parentTaskId", t.parentTaskId)
        w.name("title").value(t.title)
        w.name("description").value(t.description)
        w.name("status").value(t.status.name)
        w.name("priority").value(t.priority.toLong())
        writeNullableInt(w, "estimatedDurationMinutes", t.estimatedDurationMinutes)
        writeNullableLong(w, "scheduledAt", t.scheduledAt)
        writeNullableLong(w, "dueAt", t.dueAt)
        w.name("createdAt").value(t.createdAt)
        w.name("updatedAt").value(t.updatedAt)
        writeNullableLong(w, "completedAt", t.completedAt)
        writeNullableLong(w, "postponedAt", t.postponedAt)
        w.name("source").value(t.source.name)
        w.endObject()
    }

    private fun writeCommitment(w: JsonWriter, c: Commitment) {
        w.beginObject()
        w.name("id").value(c.id)
        w.name("userId").value(c.userId)
        writeNullableString(w, "goalId", c.goalId)
        writeNullableString(w, "planId", c.planId)
        writeNullableString(w, "taskId", c.taskId)
        writeNullableString(w, "parentCommitmentId", c.parentCommitmentId)
        w.name("title").value(c.title)
        w.name("description").value(c.description)
        w.name("committedAt").value(c.committedAt)
        writeNullableLong(w, "scheduledStartAt", c.scheduledStartAt)
        writeNullableLong(w, "scheduledEndAt", c.scheduledEndAt)
        w.name("status").value(c.status.name)
        w.name("priority").value(c.priority.toLong())
        w.name("source").value(c.source.name)
        w.name("createdAt").value(c.createdAt)
        w.name("updatedAt").value(c.updatedAt)
        writeNullableLong(w, "startedAt", c.startedAt)
        writeNullableLong(w, "completedAt", c.completedAt)
        writeNullableLong(w, "postponedAt", c.postponedAt)
        writeNullableLong(w, "missedAt", c.missedAt)
        writeNullableLong(w, "recoveredAt", c.recoveredAt)
        w.endObject()
    }

    private fun writeOutcome(w: JsonWriter, o: Outcome) {
        w.beginObject()
        w.name("id").value(o.id)
        w.name("userId").value(o.userId)
        w.name("sourceEntityId").value(o.sourceEntityId)
        w.name("sourceEntityType").value(o.sourceEntityType)
        w.name("resultStatus").value(o.resultStatus.name)
        writeNullableInt(w, "actualDurationMinutes", o.actualDurationMinutes)
        w.name("completedAt").value(o.completedAt)
        w.name("createdAt").value(o.createdAt)
        w.name("schemaVersion").value(o.schemaVersion.toLong())
        w.endObject()
    }

    private fun writeReflection(w: JsonWriter, r: Reflection) {
        w.beginObject()
        w.name("id").value(r.id)
        w.name("userId").value(r.userId)
        writeNullableString(w, "targetEntityId", r.targetEntityId)
        writeNullableString(w, "targetEntityType", r.targetEntityType)
        w.name("content").value(r.content)
        writeNullableString(w, "sentiment", r.sentiment)
        w.name("createdAt").value(r.createdAt)
        w.name("schemaVersion").value(r.schemaVersion.toLong())
        w.endObject()
    }

    private fun writeProtectionRule(w: JsonWriter, pr: ProtectionRule) {
        w.beginObject()
        w.name("id").value(pr.id)
        w.name("userId").value(pr.userId)
        writeNullableString(w, "targetPackage", pr.targetPackage)
        writeNullableString(w, "targetCategory", pr.targetCategory)
        w.name("triggerType").value(pr.triggerType.name)
        w.name("conditions").value(pr.conditions)
        w.name("enabled").value(pr.enabled)
        w.name("priority").value(pr.priority.toLong())
        w.name("createdAt").value(pr.createdAt)
        w.name("updatedAt").value(pr.updatedAt)
        w.endObject()
    }

    private fun writeProtectionSession(w: JsonWriter, ps: ProtectionSession) {
        w.beginObject()
        w.name("id").value(ps.id)
        w.name("userId").value(ps.userId)
        writeNullableString(w, "ruleId", ps.ruleId)
        writeNullableString(w, "commitmentId", ps.commitmentId)
        writeNullableString(w, "taskId", ps.taskId)
        w.name("startedAt").value(ps.startedAt)
        writeNullableLong(w, "scheduledEndAt", ps.scheduledEndAt)
        writeNullableLong(w, "endedAt", ps.endedAt)
        w.name("status").value(ps.status.name)
        w.name("source").value(ps.source.name)
        w.name("overrideAllowed").value(ps.overrideAllowed)
        w.name("createdAt").value(ps.createdAt)
        w.name("updatedAt").value(ps.updatedAt)
        w.endObject()
    }

    private fun writeEvent(w: JsonWriter, e: Event) {
        w.beginObject()
        w.name("id").value(e.id)
        w.name("userId").value(e.userId)
        w.name("type").value(e.type.name)
        writeNullableString(w, "entityType", e.entityType)
        writeNullableString(w, "entityId", e.entityId)
        w.name("occurredAt").value(e.occurredAt)
        w.name("recordedAt").value(e.recordedAt)
        writeNullableLong(w, "processedAt", e.processedAt)
        w.name("source").value(e.source.name)
        writeNullableString(w, "previousState", e.previousState)
        writeNullableString(w, "newState", e.newState)
        writeNullableString(w, "metadata", e.metadata)
        writeNullableString(w, "correlationId", e.correlationId)
        writeNullableString(w, "causationId", e.causationId)
        w.name("schemaVersion").value(e.schemaVersion.toLong())
        w.endObject()
    }

    private fun writeMemory(w: JsonWriter, m: Memory) {
        w.beginObject()
        w.name("id").value(m.id)
        w.name("userId").value(m.userId)
        w.name("type").value(m.type)
        w.name("content").value(m.content)
        w.name("source").value(m.source.name)
        w.name("confidence").value(m.confidence.toDouble())
        w.name("evidenceCount").value(m.evidenceCount.toLong())
        w.name("firstObservedAt").value(m.firstObservedAt)
        w.name("lastObservedAt").value(m.lastObservedAt)
        w.name("confirmationState").value(m.confirmationState.name)
        w.name("status").value(m.status.name)
        writeNullableLong(w, "expiresAt", m.expiresAt)
        w.name("createdAt").value(m.createdAt)
        w.name("updatedAt").value(m.updatedAt)
        w.name("schemaVersion").value(m.schemaVersion.toLong())
        w.endObject()
    }

    private fun writeObservation(w: JsonWriter, o: Observation) {
        w.beginObject()
        w.name("id").value(o.id)
        w.name("userId").value(o.userId)
        w.name("type").value(o.type.name)
        w.name("source").value(o.source.name)
        w.name("occurredAt").value(o.occurredAt)
        w.name("recordedAt").value(o.recordedAt)
        writeNullableString(w, "subjectId", o.subjectId)
        w.name("value").value(o.value)
        w.name("context").value(o.context)
        if (o.confidence != null) {
            w.name("confidence").value(o.confidence.toDouble())
        } else {
            w.name("confidence").nullValue()
        }
        w.name("provenance")
        w.beginObject()
        w.name("source").value(o.provenance.source.name)
        writeNullableString(w, "sourceReference", o.provenance.sourceReference)
        w.name("capturedAt").value(o.provenance.capturedAt)
        w.endObject()
        w.name("schemaVersion").value(o.schemaVersion.toLong())
        w.endObject()
    }

    private fun writePattern(w: JsonWriter, p: Pattern) {
        w.beginObject()
        w.name("id").value(p.id)
        w.name("userId").value(p.userId)
        w.name("fingerprint").value(p.fingerprint)
        w.name("type").value(p.type.name)
        w.name("description").value(p.description)
        writeNullableString(w, "conditions", p.conditions)
        writeNullableString(w, "predictedBehavior", p.predictedBehavior)
        w.name("confidence").value(p.confidence.toDouble())
        w.name("evidenceCount").value(p.evidenceCount.toLong())

        w.name("evidenceReferences")
        if (p.evidenceReferences != null) {
            w.beginArray()
            for (ref in p.evidenceReferences) {
                w.beginObject()
                w.name("sourceId").value(ref.sourceId)
                w.name("sourceType").value(ref.sourceType.name)
                w.endObject()
            }
            w.endArray()
        } else {
            w.nullValue()
        }

        w.name("firstObservedAt").value(p.firstObservedAt)
        w.name("lastObservedAt").value(p.lastObservedAt)
        w.name("status").value(p.status.name)
        w.name("confirmationState").value(p.confirmationState.name)
        w.name("createdAt").value(p.createdAt)
        w.name("updatedAt").value(p.updatedAt)
        w.endObject()
    }

    private fun writeAutonomySetting(w: JsonWriter, s: AutonomySettingRecord) {
        w.beginObject()
        w.name("userId").value(s.userId)
        w.name("capability").value(s.capability)
        w.name("level").value(s.level)
        w.name("updatedAt").value(s.updatedAt)
        w.endObject()
    }

    private fun writeGlobalAutonomyState(w: JsonWriter, s: GlobalAutonomyStateRecord) {
        w.beginObject()
        w.name("userId").value(s.userId)
        w.name("isGlobalPauseActive").value(s.isGlobalPauseActive)
        w.name("updatedAt").value(s.updatedAt)
        w.endObject()
    }

    private fun writeAppUsageSettings(w: JsonWriter, s: AppUsageObservationSettings) {
        w.beginObject()
        w.name("userId").value(s.userId)
        w.name("isEnabled").value(s.isEnabled)
        w.endObject()
    }

    private fun writeInterventionRecord(w: JsonWriter, ir: InterventionRecord) {
        w.beginObject()
        w.name("id").value(ir.id)
        w.name("userId").value(ir.userId)
        w.name("type").value(ir.type.name)
        w.name("state").value(ir.state.name)
        w.name("title").value(ir.title)
        w.name("description").value(ir.description)
        w.name("channel").value(ir.channel.name)
        writeNullableString(w, "resolutionReason", ir.resolutionReason?.name)
        writeNullableString(w, "contextData", ir.contextData)
        w.name("createdAt").value(ir.createdAt)
        w.name("updatedAt").value(ir.updatedAt)
        w.endObject()
    }

    private fun writeNotificationSettings(w: JsonWriter, s: NotificationObservationSettings) {
        w.beginObject()
        w.name("userId").value(s.userId)
        w.name("isEnabled").value(s.isEnabled)
        w.endObject()
    }

    private fun writeCalendarSettings(w: JsonWriter, s: CalendarObservationSettings) {
        w.beginObject()
        w.name("userId").value(s.userId)
        w.name("isEnabled").value(s.isEnabled)
        w.name("updatedAt").value(s.updatedAt)
        w.endObject()
    }

    private fun writeLocationSettings(w: JsonWriter, s: LocationObservationSettings) {
        w.beginObject()
        w.name("userId").value(s.userId)
        w.name("isEnabled").value(s.isEnabled)
        w.name("updatedAt").value(s.updatedAt)
        w.endObject()
    }

    private fun writeActivitySettings(w: JsonWriter, s: ActivityObservationSettings) {
        w.beginObject()
        w.name("userId").value(s.userId)
        w.name("isEnabled").value(s.isEnabled)
        w.name("lastUpdatedAt").value(s.lastUpdatedAt)
        w.endObject()
    }

    private fun writeExperimentRecord(w: JsonWriter, e: ExperimentRecord) {
        w.beginObject()
        w.name("id").value(e.id)
        w.name("userId").value(e.userId)
        w.name("hypothesis").value(e.hypothesis)
        w.name("activeVariation").value(e.activeVariation)
        w.name("controlVariation").value(e.controlVariation)
        w.name("targetMetric").value(e.targetMetric)
        w.name("state").value(e.state.name)
        w.name("startedAt").value(e.startedAt)
        writeNullableLong(w, "endedAt", e.endedAt)
        writeNullableString(w, "outcomeSummary", e.outcomeSummary)
        w.endObject()
    }

    private fun writeBarrierHypothesis(w: JsonWriter, b: BarrierHypothesis) {
        w.beginObject()
        w.name("id").value(b.id)
        w.name("userId").value(b.userId)
        w.name("category").value(b.category.name)
        w.name("description").value(b.description)
        w.name("confirmationState").value(b.confirmationState.name)
        w.name("status").value(b.status.name)
        w.name("sourceReflectionId").value(b.sourceReflectionId)
        w.name("firstObservedAt").value(b.firstObservedAt)
        w.name("lastObservedAt").value(b.lastObservedAt)
        w.endObject()
    }

    private fun writeInterventionRecommendation(w: JsonWriter, r: InterventionRecommendationExportRecord) {
        w.beginObject()
        w.name("id").value(r.id)
        w.name("userId").value(r.userId)
        w.name("interventionType").value(r.interventionType)
        writeNullableString(w, "objective", r.objective)
        writeNullableString(w, "targetEntityId", r.targetEntityId)
        writeNullableString(w, "targetEntityType", r.targetEntityType)
        w.name("rationale").value(r.rationale)
        w.name("suggestedAction").value(r.suggestedAction)
        w.name("status").value(r.status)
        w.name("createdAt").value(r.createdAt)
        writeNullableLong(w, "expiresAt", r.expiresAt)
        writeNullableString(w, "causalHypothesisId", r.causalHypothesisId)
        writeNullableString(w, "causalHypothesisType", r.causalHypothesisType)
        w.endObject()
    }

    private fun writeLearnerEventStatus(w: JsonWriter, s: LearnerEventStatusRecord) {
        w.beginObject()
        w.name("eventId").value(s.eventId)
        w.name("userId").value(s.userId)
        w.name("status").value(s.status)
        w.name("claimCount").value(s.claimCount.toLong())
        w.name("failureCount").value(s.failureCount.toLong())
        w.name("maxAttempts").value(s.maxAttempts.toLong())
        writeNullableString(w, "leaseToken", s.leaseToken)
        writeNullableLong(w, "leaseExpiresAt", s.leaseExpiresAt)
        writeNullableLong(w, "lastAttemptAt", s.lastAttemptAt)
        w.name("nextAttemptAt").value(s.nextAttemptAt)
        writeNullableString(w, "errorMessage", s.errorMessage)
        w.name("hadMalformedMetadata").value(s.hadMalformedMetadata)
        w.name("createdAt").value(s.createdAt)
        w.name("updatedAt").value(s.updatedAt)
        w.endObject()
    }

    private fun writeCorrectionReview(w: JsonWriter, c: CorrectionReviewItem) {
        w.beginObject()
        w.name("id").value(c.id)
        w.name("userId").value(c.userId)
        w.name("sourceEventId").value(c.sourceEventId)
        w.name("recommendationId").value(c.recommendationId)

        w.name("causalHypothesis")
        w.beginObject()
        w.name("id").value(c.causalHypothesis.id)
        w.name("type").value(c.causalHypothesis.type.name)
        w.endObject()

        w.name("correctionScope").value(c.correctionScope.name)
        w.name("correctedText").value(c.correctedText)
        w.name("decisionState").value(c.decisionState)
        w.name("reviewStatus").value(c.reviewStatus.name)
        writeNullableString(w, "reviewFeedback", c.reviewFeedback)
        w.name("createdAt").value(c.createdAt)
        w.name("updatedAt").value(c.updatedAt)
        writeNullableLong(w, "reviewedAt", c.reviewedAt)
        w.name("schemaVersion").value(c.schemaVersion.toLong())
        w.endObject()
    }

    // --- Helper Nullable Primitive Writers ---

    private fun writeNullableString(w: JsonWriter, name: String, value: String?) {
        w.name(name)
        if (value != null) {
            w.value(value)
        } else {
            w.nullValue()
        }
    }

    private fun writeNullableLong(w: JsonWriter, name: String, value: Long?) {
        w.name(name)
        if (value != null) {
            w.value(value)
        } else {
            w.nullValue()
        }
    }

    private fun writeNullableInt(w: JsonWriter, name: String, value: Int?) {
        w.name(name)
        if (value != null) {
            w.value(value.toLong())
        } else {
            w.nullValue()
        }
    }

    private fun writeNullableDouble(w: JsonWriter, name: String, value: Double?) {
        w.name(name)
        if (value != null) {
            w.value(value)
        } else {
            w.nullValue()
        }
    }

    private fun writeNullableBoolean(w: JsonWriter, name: String, value: Boolean?) {
        w.name(name)
        if (value != null) {
            w.value(value)
        } else {
            w.nullValue()
        }
    }
}
