package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.sanket_satpute_20.ironmind.domain.model.observation.LocationSnapshot
import com.sanket_satpute_20.ironmind.domain.provider.LocationObservationProvider

class AndroidLocationObservationProvider(
    private val context: Context
) : LocationObservationProvider {

    override fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun getLastKnownCoarseLocation(): LocationSnapshot? {
        if (!isPermissionGranted()) return null

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try network provider first (coarse); fall back to GPS if needed.
            // We only read a single last-known snapshot — no continuous updates.
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            var location: android.location.Location? = null
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    @Suppress("MissingPermission")
                    location = locationManager.getLastKnownLocation(provider)
                    if (location != null) break
                }
            }

            location?.let {
                LocationSnapshot(
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            }
        } catch (e: Exception) {
            println("AndroidLocationObservationProvider: Failed to get location - ${e.message}")
            null
        }
    }
}
