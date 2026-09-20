package com.sanket_satpute_20.ironmind.domain.provider

import com.sanket_satpute_20.ironmind.domain.model.observation.LocationSnapshot

interface LocationObservationProvider {
    /**
     * Returns true if ACCESS_COARSE_LOCATION permission is granted.
     */
    fun isPermissionGranted(): Boolean

    /**
     * Returns the last known coarse location snapshot, or null if unavailable.
     * This is a single snapshot — not continuous tracking.
     */
    fun getLastKnownCoarseLocation(): LocationSnapshot?
}
