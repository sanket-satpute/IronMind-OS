package com.sanket_satpute_20.ironmind.domain.provider

import com.sanket_satpute_20.ironmind.domain.model.observation.CalendarEvent

interface CalendarObservationProvider {
    /**
     * Returns true if the READ_CALENDAR permission is granted.
     */
    fun isPermissionGranted(): Boolean

    /**
     * Retrieves upcoming calendar events within the given time range.
     * Only returns if permission is granted, otherwise returns empty list.
     */
    suspend fun getUpcomingEvents(fromTime: Long, toTime: Long): List<CalendarEvent>
}
