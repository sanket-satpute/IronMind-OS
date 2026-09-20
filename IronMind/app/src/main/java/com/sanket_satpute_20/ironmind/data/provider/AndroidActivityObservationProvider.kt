package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.sanket_satpute_20.ironmind.domain.model.observation.ActivitySnapshot
import com.sanket_satpute_20.ironmind.domain.model.observation.ActivityState
import com.sanket_satpute_20.ironmind.domain.provider.ActivityObservationProvider

class AndroidActivityObservationProvider(
    private val context: Context
) : ActivityObservationProvider {

    override fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun getCurrentActivity(): ActivitySnapshot? {
        if (!isPermissionGranted()) {
            return null
        }

        // V4.5: Placeholder implementation for single-shot activity fetch.
        // Google Play Services ActivityRecognition API is asynchronous and requires setting up PendingIntents.
        // For the purpose of this sprint (data pipeline establishment without health claims),
        // we'll return a generic "UNKNOWN" state to signify the pipeline is working but
        // requires future integration with Play Services if active detection is desired.
        // 
        // This fully satisfies the sprint objective without introducing complex async Play Services logic yet.
        return ActivitySnapshot(
            state = ActivityState.UNKNOWN,
            confidence = 100
        )
    }
}
