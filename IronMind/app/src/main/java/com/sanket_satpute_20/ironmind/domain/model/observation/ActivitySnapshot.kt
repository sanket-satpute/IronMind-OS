package com.sanket_satpute_20.ironmind.domain.model.observation

enum class ActivityState {
    IN_VEHICLE,
    ON_BICYCLE,
    ON_FOOT,
    STILL,
    UNKNOWN,
    TILTING,
    WALKING,
    RUNNING
}

data class ActivitySnapshot(
    val state: ActivityState,
    val confidence: Int
)
