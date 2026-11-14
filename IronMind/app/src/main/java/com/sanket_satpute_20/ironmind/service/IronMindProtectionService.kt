package com.sanket_satpute_20.ironmind.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.data.provider.AndroidAppProtectionProvider
import com.sanket_satpute_20.ironmind.data.provider.AndroidInstalledAppDiscoveryProvider
import com.sanket_satpute_20.ironmind.data.provider.ProtectionEnforcementStore
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetAppRestrictionConfigsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetEligibleRestrictionsForEnforcementUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.AuthoritativeProtectionSessionRecoveryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IronMindProtectionService : AccessibilityService() {

    companion object {
        private const val TAG = "IronMindProtection"
        private var isActive = false
        private var isConnected = false
        private var isRecovering = false
        private val protectedPkgs = mutableSetOf<String>()
        private var activeUserId: String? = null
        private var activeSessionId: String? = null
        private var activeScheduledEndAt: Long? = null

        var clock: com.sanket_satpute_20.ironmind.domain.common.Clock? = null
        private fun currentTime(): Long = clock?.currentTimeMillis() ?: System.currentTimeMillis()

        var recoveryUseCaseProvider: (() -> AuthoritativeProtectionSessionRecoveryUseCase)? = null

        fun isServiceConnected(): Boolean = isConnected
        fun setServiceConnected(connected: Boolean) {
            isConnected = connected
        }

        fun isServiceRecovering(): Boolean = isRecovering
        fun setServiceRecovering(recovering: Boolean) {
            isRecovering = recovering
        }

        fun setProtectionActive(active: Boolean) {
            isActive = active
            if (!active) {
                protectedPkgs.clear()
                activeUserId = null
                activeSessionId = null
                activeScheduledEndAt = null
            }
        }

        fun isProtectionActive(): Boolean {
            val endAt = activeScheduledEndAt
            if (isActive && endAt != null && currentTime() >= endAt) {
                setProtectionActive(false)
                return false
            }
            return isActive
        }

        fun setProtectedPackages(packages: Collection<String>) {
            protectedPkgs.clear()
            protectedPkgs.addAll(packages.filter { it.isNotBlank() && it != ProtectionEnforcementStore.IRONMIND_PACKAGE })
        }

        fun getProtectedPackages(): Set<String> {
            val endAt = activeScheduledEndAt
            if (isActive && endAt != null && currentTime() >= endAt) {
                setProtectionActive(false)
                return emptySet()
            }
            return protectedPkgs.toSet()
        }

        fun setActiveSessionIdentity(sessionId: String?, userId: String?, scheduledEndAt: Long? = null) {
            activeSessionId = sessionId
            activeUserId = userId
            activeScheduledEndAt = scheduledEndAt
        }

        fun getActiveSessionId(): String? = activeSessionId
        fun getActiveUserId(): String? = activeUserId
        fun getActiveScheduledEndAt(): Long? = activeScheduledEndAt
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "IronMindLifecycle [Protection] [SERVICE_CONNECTED]")
        setServiceConnected(true)
        rehydrateState()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "IronMindLifecycle [Protection] [SERVICE_UNBOUND]")
        setServiceConnected(false)
        setProtectionActive(false)
        setActiveSessionIdentity(null, null, null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "IronMindLifecycle [Protection] [SERVICE_DESTROYED]")
        setServiceConnected(false)
        setProtectionActive(false)
        setActiveSessionIdentity(null, null, null)
        super.onDestroy()
    }

    fun rehydrateState(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        val app = applicationContext as? IronMindApplication
        val container = app?.container
        val recoveryUseCase = recoveryUseCaseProvider?.invoke()
            ?: if (container != null) {
                val store = (container.appProtectionProvider as? AndroidAppProtectionProvider)?.enforcementStore
                    ?: ProtectionEnforcementStore(applicationContext)
                AuthoritativeProtectionSessionRecoveryUseCase(
                    protectionRepository = container.protectionRepository,
                    userSessionProvider = container.userSessionProvider,
                    clock = container.clock,
                    appProtectionProvider = container.appProtectionProvider,
                    enforcementStore = store,
                    getEligibleRestrictionsForEnforcementUseCase = GetEligibleRestrictionsForEnforcementUseCase(
                        getAppRestrictionConfigsUseCase = GetAppRestrictionConfigsUseCase(container.protectionRepository),
                        installedAppDiscoveryProvider = AndroidInstalledAppDiscoveryProvider(applicationContext)
                    ),
                    eventRepository = container.eventRepository
                )
            } else null

        if (recoveryUseCase != null) {
            setServiceRecovering(true)
            scope.launch {
                try {
                    val result = recoveryUseCase()
                    Log.i(TAG, "IronMindLifecycle [Protection] [RECOVERY_EVALUATED] result=$result")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to evaluate authoritative protection recovery: ${e.message}")
                    setProtectionActive(false)
                    setProtectedPackages(emptySet())
                    setActiveSessionIdentity(null, null, null)
                } finally {
                    setServiceRecovering(false)
                }
            }
        } else {
            setServiceRecovering(false)
            // No authoritative recovery use case available -> fail closed immediately
            try {
                ProtectionEnforcementStore(applicationContext).clearEnforcementState()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear enforcement store: ${e.message}")
            }
            setProtectionActive(false)
            setProtectedPackages(emptySet())
            setActiveSessionIdentity(null, null, null)
            Log.w(TAG, "IronMindLifecycle [Protection] [RECOVERY_FAILED_NO_CONTAINER] failed closed")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val endAt = activeScheduledEndAt
        if (isActive && endAt != null && currentTime() >= endAt) {
            Log.i(TAG, "IronMindLifecycle [Protection] [SESSION_EXPIRED] scheduledEndAt=$endAt")
            setProtectionActive(false)
            rehydrateState()
            return
        }

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