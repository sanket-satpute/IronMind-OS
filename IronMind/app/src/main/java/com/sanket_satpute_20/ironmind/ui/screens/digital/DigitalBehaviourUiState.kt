package com.sanket_satpute_20.ironmind.ui.screens.digital

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.digital.DailyAppUsageIntelligence
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.category
import java.time.LocalDate

data class DigitalBehaviourUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val dailyUsage: DailyAppUsageIntelligence? = null,
    val isUsageAccessPermissionGranted: Boolean = false,
    val isUsageObservationEnabled: Boolean = true,
    val activeProtectionSessions: List<ProtectionSession> = emptyList(),
    val errorMessage: String? = null,
    val patterns: List<Pattern> = emptyList(),
    val insights: List<PersonalInsight> = emptyList(),
    val barriers: List<BarrierHypothesis> = emptyList(),
    val isSubmittingAction: Boolean = false,
    val actionError: String? = null
) {
    val effectiveAvailability: SourceAvailability
        get() = when {
            !isUsageAccessPermissionGranted -> SourceAvailability.PERMISSION_DENIED
            !isUsageObservationEnabled -> SourceAvailability.PERMISSION_NOT_REQUESTED
            dailyUsage != null -> dailyUsage.availability
            else -> SourceAvailability.EMPTY_NO_DATA
        }

    val digitalPatterns: List<Pattern>
        get() = patterns.filter { pattern ->
            pattern.status != PatternStatus.DELETED &&
                (pattern.category == PatternCategory.DIGITAL_BEHAVIOUR ||
                 pattern.category == PatternCategory.CROSS_DOMAIN ||
                 pattern.category == PatternCategory.FOCUS)
        }

    val digitalInsights: List<PersonalInsight>
        get() = insights.filter { insight ->
            insight.category == InsightCategory.DIGITAL_BEHAVIOUR ||
                insight.category == InsightCategory.FOCUS ||
                insight.category == InsightCategory.CROSS_DOMAIN
        }

    val digitalBarriers: List<BarrierHypothesis>
        get() = barriers.filter { barrier ->
            barrier.status != BarrierStatus.EXPIRED &&
                (barrier.category == BarrierCategory.DISTRACTION ||
                 barrier.category == BarrierCategory.ENVIRONMENTAL_FRICTION ||
                 barrier.supportingPatternIds.any { pid -> digitalPatterns.any { dp -> dp.id == pid } })
        }
}
