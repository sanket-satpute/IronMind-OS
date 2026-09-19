package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionTriggerType

@Entity(tableName = "protection_rules")
data class ProtectionRuleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val targetPackage: String?,
    val targetCategory: String?,
    val triggerType: String,
    val conditions: String,
    val enabled: Boolean,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long
)

fun ProtectionRuleEntity.toDomain(): ProtectionRule {
    return ProtectionRule(
        id = id,
        userId = userId,
        targetPackage = targetPackage,
        targetCategory = targetCategory,
        triggerType = ProtectionTriggerType.valueOf(triggerType),
        conditions = conditions,
        enabled = enabled,
        priority = priority,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ProtectionRule.toEntity(): ProtectionRuleEntity {
    return ProtectionRuleEntity(
        id = id,
        userId = userId,
        targetPackage = targetPackage,
        targetCategory = targetCategory,
        triggerType = triggerType.name,
        conditions = conditions,
        enabled = enabled,
        priority = priority,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
