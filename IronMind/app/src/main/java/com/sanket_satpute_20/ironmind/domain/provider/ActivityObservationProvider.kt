package com.sanket_satpute_20.ironmind.domain.provider

import com.sanket_satpute_20.ironmind.domain.model.observation.ActivitySnapshot

interface ActivityObservationProvider {
    fun isPermissionGranted(): Boolean
    fun getCurrentActivity(): ActivitySnapshot?
}
