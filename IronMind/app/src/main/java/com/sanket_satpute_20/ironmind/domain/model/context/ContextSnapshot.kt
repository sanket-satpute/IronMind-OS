package com.sanket_satpute_20.ironmind.domain.model.context

import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.Reflection

data class ContextSnapshot(
    val timestamp: Long,
    val dayOfWeek: Int,
    val activeCommitments: List<Commitment>,
    val recentEvents: List<Event>,
    val recentObservations: List<Observation>,
    val recentReflections: List<Reflection>,
    val activeProtectionSession: ProtectionSession?,
    val activeGoals: List<Goal>
)
