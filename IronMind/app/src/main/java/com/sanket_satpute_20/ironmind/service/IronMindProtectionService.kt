package com.sanket_satpute_20.ironmind.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                Log.i(TAG, "IronMindLifecycle [Protection] [APP_DETECTED] package=$packageName")
                
                val blocked = performGlobalAction(GLOBAL_ACTION_HOME)
                if (blocked) {
                    Log.i(TAG, "IronMindLifecycle [Protection] [BLOCK_SUCCESS] package=$packageName")
                    // Show a toast from Main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(applicationContext, "IronMind: App blocked", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.i(TAG, "IronMindLifecycle [Protection] [BLOCK_FAILURE] package=$packageName reason=GLOBAL_ACTION_HOME_FAILED")
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required method, nothing to do here
    }
}
