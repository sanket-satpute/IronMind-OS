package com.sanket_satpute_20.ironmind.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.sanket_satpute_20.ironmind.data.provider.ProtectionEnforcementStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IronMindProtectionService : AccessibilityService() {

    companion object {
        private const val TAG = "IronMindProtection"
        private var isActive = false
        private val protectedPkgs = mutableSetOf<String>()

        fun setProtectionActive(active: Boolean) {
            isActive = active
            if (!active) {
                protectedPkgs.clear()
            }
        }

        fun isProtectionActive(): Boolean {
            return isActive
        }

        fun setProtectedPackages(packages: Collection<String>) {
            protectedPkgs.clear()
            protectedPkgs.addAll(packages.filter { it.isNotBlank() && it != ProtectionEnforcementStore.IRONMIND_PACKAGE })
        }

        fun getProtectedPackages(): Set<String> {
            return protectedPkgs.toSet()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        rehydrateState()
    }

    fun rehydrateState() {
        try {
            val store = ProtectionEnforcementStore(applicationContext)
            val state = store.getEnforcementState()
            if (state != null && state.isActive && state.protectedPackages.isNotEmpty()) {
                setProtectedPackages(state.protectedPackages)
                setProtectionActive(true)
                Log.i(TAG, "IronMindLifecycle [Protection] [SERVICE_REHYDRATED] user=${state.userId} count=${state.protectedPackages.size}")
            } else {
                setProtectionActive(false)
                setProtectedPackages(emptySet())
                Log.i(TAG, "IronMindLifecycle [Protection] [SERVICE_REHYDRATED_IDLE]")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rehydrate protection state: ${e.message}")
            setProtectionActive(false)
            setProtectedPackages(emptySet())
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isActive || protectedPkgs.isEmpty()) return

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Critical Safety Invariant: Never evict IronMind itself
            if (packageName == applicationContext.packageName || packageName == ProtectionEnforcementStore.IRONMIND_PACKAGE) {
                return
            }

            if (protectedPkgs.contains(packageName)) {
                Log.i(TAG, "IronMindLifecycle [Protection] [APP_DETECTED] package=$packageName")

                val blocked = performGlobalAction(GLOBAL_ACTION_HOME)
                if (blocked) {
                    Log.i(TAG, "IronMindLifecycle [Protection] [BLOCK_SUCCESS] package=$packageName")
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(applicationContext, "IronMind: App restricted during active protection", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.i(TAG, "IronMindLifecycle [Protection] [BLOCK_FAILURE] package=$packageName reason=GLOBAL_ACTION_HOME_FAILED")
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required method for accessibility service
    }
}
