package com.sanket_satpute_20.ironmind.domain.usecase.data

import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation

data class UserDataExport(
    val profile: UserProfile?,
    val goals: List<Goal>,
    val tasks: List<Task>,
    val commitments: List<Commitment>,
    val patterns: List<Pattern>,
    val memories: List<Memory>,
    val observations: List<Observation>,
    val events: List<Event>,
    val interventions: List<InterventionRecord>,
    val exportedAt: Long
)
