package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType

@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val source: String,
    val occurredAt: Long,
    val recordedAt: Long,
    val subjectId: String?,
    val value: String,
    val context: String,
    val confidence: Float?,
    val provenanceSource: String,
    val provenanceSourceReference: String?,
    val provenanceCapturedAt: Long,
    val schemaVersion: Int
)

fun ObservationEntity.toDomain(): Observation = Observation(
    id = id,
    userId = userId,
    type = ObservationType.valueOf(type),
    source = ObservationSource.valueOf(source),
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    subjectId = subjectId,
    value = value,
    context = context,
    confidence = confidence,
    provenance = ObservationProvenance(
        source = ObservationSource.valueOf(provenanceSource),
        sourceReference = provenanceSourceReference,
        capturedAt = provenanceCapturedAt
    ),
    schemaVersion = schemaVersion
)

fun Observation.toEntity(): ObservationEntity = ObservationEntity(
    id = id,
    userId = userId,
    type = type.name,
    source = source.name,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    subjectId = subjectId,
    value = value,
    context = context,
    confidence = confidence,
    provenanceSource = provenance.source.name,
    provenanceSourceReference = provenance.sourceReference,
    provenanceCapturedAt = provenance.capturedAt,
    schemaVersion = schemaVersion
)
