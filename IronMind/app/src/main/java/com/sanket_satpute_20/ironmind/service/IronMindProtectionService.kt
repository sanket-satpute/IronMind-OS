package com.sanket_satpute_20.ironmind.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class IronMindProtectionService : AccessibilityService() {

    companion object {
        private const val TAG = "IronMindProtection"
        private var isActive = false
        private val protectedPkgs = mutableListOf<String>()

        fun setProtectionActive(active: Boolean) {
            isActive = active
        }

        fun isProtectionActive(): Boolean {
            return isActive
        }

        fun setProtectedPackages(packages: List<String>) {
            protectedPkgs.clear()
            protectedPkgs.addAll(packages)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isActive) return

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            if (protectedPkgs.contains(packageName)) {
                Log.i(TAG, "IronMindLifecycle [Protection] [INTERVENTION_TRIGGERED] package=$packageName")
                // In Sprint V0.8 we only log the intervention to show the adapter works.
                // Future sprints will show the full blocking UI or redirect to Home.
            }
        }
    }

    override fun onInterrupt() {
        // Required method, nothing to do here
    }
}
