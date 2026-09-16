package com.sanket_satpute_20.ironmind.infrastructure.logging

import android.util.Log
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger

/**
 * Implementation of IronLogger that outputs to Android Logcat.
 * Enforces the standardized IronMind logging format and filters sensitive data.
 */
class DebugIronLogger : IronLogger {

    companion object {
        private const val TAG = "IronMindLifecycle"
        
        // Keys that should have their values masked in the logs
        private val SENSITIVE_KEYS = setOf(
            "password", "token", "secret", "email", "auth", "credential"
        )
        
        private const val MASKED_VALUE = "***"
    }

    override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {
        val formattedMessage = formatMessage(component, event, parameters)
        // We use Log.d for debug lifecycle events
        Log.d(TAG, formattedMessage)
    }

    /**
     * Formats the message according to the rule:
     * IronMindLifecycle [Component] [EVENT] key=value...
     * Visible for testing.
     */
    internal fun formatMessage(component: String, event: String, parameters: Map<String, Any?>): String {
        val baseMessage = "[$component] [$event]"
        
        if (parameters.isEmpty()) {
            return baseMessage
        }

        val paramString = parameters.entries.joinToString(separator = " ") { (key, value) ->
            val safeValue = if (isSensitive(key)) MASKED_VALUE else value.toString()
            "$key=$safeValue"
        }

        return "$baseMessage $paramString"
    }

    private fun isSensitive(key: String): Boolean {
        val lowerKey = key.lowercase()
        return SENSITIVE_KEYS.any { lowerKey.contains(it) }
    }
}
