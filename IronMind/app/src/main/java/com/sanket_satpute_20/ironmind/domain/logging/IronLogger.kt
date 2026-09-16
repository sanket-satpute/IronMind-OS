package com.sanket_satpute_20.ironmind.domain.logging

/**
 * Centralized logging abstraction.
 * Allows the domain and other layers to log lifecycle events without
 * depending on Android framework classes (like android.util.Log).
 */
interface IronLogger {
    /**
     * Logs a standard lifecycle event.
     *
     * @param component The component emitting the event (e.g., "App", "Database", "Goal")
     * @param event The action or state change (e.g., "STARTED", "CREATED")
     * @param parameters Key-value pairs providing context for the event
     */
    fun logLifecycle(component: String, event: String, parameters: Map<String, Any?> = emptyMap())
}
