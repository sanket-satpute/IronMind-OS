package com.sanket_satpute_20.ironmind.domain.model.digital

import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionTriggerType
import org.json.JSONObject

/**
 * Encodes and decodes [AppRestrictionConfig] to and from [ProtectionRule].
 * Reuses the existing Protection persistence architecture without schema migrations.
 */
object AppRestrictionConfigCodec {
    private const val KEY_APP_NAME = "appDisplayName"
    private const val KEY_SCHEDULE_REF = "scheduleReferenceId"

    fun toProtectionRule(config: AppRestrictionConfig): ProtectionRule {
        val conditionsJson = JSONObject().apply {
            put(KEY_APP_NAME, config.appDisplayName)
            if (config.scheduleReferenceId != null) {
                put(KEY_SCHEDULE_REF, config.scheduleReferenceId)
            }
        }.toString()

        return ProtectionRule(
            id = config.id,
            userId = config.userId,
            targetPackage = config.packageName,
            targetCategory = null,
            triggerType = ProtectionTriggerType.MANUAL,
            conditions = conditionsJson,
            enabled = config.isEnabled,
            priority = 0,
            createdAt = config.createdAt,
            updatedAt = config.updatedAt
        )
    }

    fun fromProtectionRule(rule: ProtectionRule): AppRestrictionConfig? {
        val pkg = rule.targetPackage ?: return null
        var appName = pkg
        var scheduleRef: String? = null

        if (rule.conditions.isNotBlank()) {
            try {
                val json = JSONObject(rule.conditions)
                appName = json.optString(KEY_APP_NAME, pkg)
                if (json.has(KEY_SCHEDULE_REF)) {
                    scheduleRef = json.optString(KEY_SCHEDULE_REF)
                }
            } catch (e: Exception) {
                appName = pkg
            }
        }

        return AppRestrictionConfig(
            id = rule.id,
            userId = rule.userId,
            packageName = pkg,
            appDisplayName = appName,
            isEnabled = rule.enabled,
            createdAt = rule.createdAt,
            updatedAt = rule.updatedAt,
            scheduleReferenceId = scheduleRef
        )
    }
}
