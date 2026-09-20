package com.sanket_satpute_20.ironmind.domain.engine

interface GoalResurfacingEngine {
    /**
     * Evaluates all active goals for the given user to determine if any are neglected.
     * If neglected, proposes an ASK intervention to the InterventionExecutionPipeline.
     */
    suspend fun evaluateNeglectedGoals(userId: String)
}
