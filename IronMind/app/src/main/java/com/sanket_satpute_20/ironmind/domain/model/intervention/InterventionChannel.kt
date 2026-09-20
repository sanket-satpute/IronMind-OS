package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Defines the execution channels through which an intervention can be delivered.
 * Used for adaptive channel selection to choose the least intrusive effective channel.
 */
enum class InterventionChannel {
    IN_APP,
    NOTIFICATION,
    PROTECTION,
    SCHEDULING_SUGGESTION,
    OTHER
}
