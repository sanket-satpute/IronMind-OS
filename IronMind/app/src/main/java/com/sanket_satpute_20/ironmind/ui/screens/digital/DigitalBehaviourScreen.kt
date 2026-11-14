package com.sanket_satpute_20.ironmind.ui.screens.digital

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.digital.AppUsageItem
import com.sanket_satpute_20.ironmind.domain.model.digital.DailyAppUsageIntelligence
import com.sanket_satpute_20.ironmind.domain.model.digital.UsageSourceProvenance
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternLifecycleState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.category
import com.sanket_satpute_20.ironmind.domain.model.pattern.lifecycleState
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalBehaviourScreen(
    modifier: Modifier = Modifier,
    viewModel: DigitalBehaviourViewModel = viewModel(factory = DigitalBehaviourViewModel.Factory),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Digital Behaviour & Intelligence") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        DigitalBehaviourContent(
            modifier = Modifier.padding(paddingValues),
            uiState = uiState,
            onSelectDate = { viewModel.selectDate(it) },
            onRefresh = { viewModel.refresh() },
            onAcknowledgeError = { viewModel.acknowledgeError() },
            onConfirmPattern = { viewModel.confirmPattern(it) },
            onDismissPattern = { viewModel.dismissPattern(it) },
            onRefutePattern = { viewModel.refutePattern(it) },
            onMarkInsightUseful = { viewModel.markInsightUseful(it) },
            onDismissInsight = { viewModel.dismissInsight(it) },
            onReviewInsightLater = { viewModel.reviewInsightLater(it) },
            onConfirmBarrier = { viewModel.confirmBarrier(it) },
            onDismissBarrier = { viewModel.dismissBarrier(it) },
            onRefuteBarrier = { viewModel.refuteBarrier(it) },
            onClearActionError = { viewModel.clearActionError() }
        )
    }
}

@Composable
fun DigitalBehaviourContent(
    modifier: Modifier = Modifier,
    uiState: DigitalBehaviourUiState,
    onSelectDate: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onAcknowledgeError: () -> Unit,
    onConfirmPattern: (String) -> Unit = {},
    onDismissPattern: (String) -> Unit = {},
    onRefutePattern: (String) -> Unit = {},
    onMarkInsightUseful: (String) -> Unit = {},
    onDismissInsight: (String) -> Unit = {},
    onReviewInsightLater: (String) -> Unit = {},
    onConfirmBarrier: (String) -> Unit = {},
    onDismissBarrier: (String) -> Unit = {},
    onRefuteBarrier: (String) -> Unit = {},
    onClearActionError: () -> Unit = {}
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Date Selector Header
        DateSelectorHeader(
            selectedDate = uiState.selectedDate,
            today = today,
            onSelectDate = onSelectDate
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isLoading && uiState.dailyUsage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // General Error banner if any
            if (uiState.errorMessage != null) {
                item {
                    ErrorBanner(
                        message = uiState.errorMessage,
                        onRetry = onAcknowledgeError
                    )
                }
            }

            // Action Error banner if any
            if (uiState.actionError != null) {
                item {
                    ActionErrorBanner(
                        message = uiState.actionError,
                        onDismiss = onClearActionError
                    )
                }
            }

            // Permission Warning Card
            if (!uiState.isUsageAccessPermissionGranted || uiState.effectiveAvailability == SourceAvailability.PERMISSION_DENIED) {
                item {
                    PermissionWarningCard(
                        context = context,
                        onRecheck = onRefresh
                    )
                }
            } else if (!uiState.isUsageObservationEnabled || uiState.effectiveAvailability == SourceAvailability.PERMISSION_NOT_REQUESTED) {
                item {
                    SettingsDisabledCard()
                }
            }

            // Daily Usage Summary Card
            item {
                DailyUsageSummaryCard(
                    dailyUsage = uiState.dailyUsage,
                    availability = uiState.effectiveAvailability
                )
            }

            // Focus & Protection Summary Card
            item {
                FocusProtectionSummaryCard(
                    activeSessions = uiState.activeProtectionSessions
                )
            }

            // Most Used Applications Header & Items
            item {
                Text(
                    text = "Most Used Applications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val apps = uiState.dailyUsage?.mostUsedApps.orEmpty()
            if (apps.isEmpty()) {
                item {
                    EmptyUsageCard(availability = uiState.effectiveAvailability)
                }
            } else {
                items(apps) { appItem ->
                    AppUsageListItem(item = appItem)
                }
            }

            // Observation Notes if available
            val notes = uiState.dailyUsage?.notes
            if (!notes.isNullOrBlank()) {
                item {
                    ObservationNotesCard(note = notes)
                }
            }

            // =================================================================
            // SECTION A: Behavioural Patterns
            // =================================================================
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Discovered Behaviour Patterns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val patterns = uiState.digitalPatterns
            if (patterns.isEmpty()) {
                item {
                    EmptyPatternCard(availability = uiState.effectiveAvailability)
                }
            } else {
                items(patterns, key = { "pattern_${it.id}" }) { pattern ->
                    PatternItemCard(
                        pattern = pattern,
                        isSubmitting = uiState.isSubmittingAction,
                        onConfirm = { onConfirmPattern(pattern.id) },
                        onDismiss = { onDismissPattern(pattern.id) },
                        onRefute = { onRefutePattern(pattern.id) }
                    )
                }
            }

            // =================================================================
            // SECTION B: Personal Insights
            // =================================================================
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Personal Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val insights = uiState.digitalInsights
            if (insights.isEmpty()) {
                item {
                    EmptyInsightCard(availability = uiState.effectiveAvailability)
                }
            } else {
                items(insights, key = { "insight_${it.id}" }) { insight ->
                    PersonalInsightItemCard(
                        insight = insight,
                        isSubmitting = uiState.isSubmittingAction,
                        onMarkUseful = { onMarkInsightUseful(insight.id) },
                        onDismiss = { onDismissInsight(insight.id) },
                        onReviewLater = { onReviewInsightLater(insight.id) }
                    )
                }
            }

            // =================================================================
            // SECTION C: Barrier Hypotheses
            // =================================================================
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Identified Barriers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val barriers = uiState.digitalBarriers
            if (barriers.isEmpty()) {
                item {
                    EmptyBarrierCard(availability = uiState.effectiveAvailability)
                }
            } else {
                items(barriers, key = { "barrier_${it.id}" }) { barrier ->
                    BarrierHypothesisItemCard(
                        barrier = barrier,
                        isSubmitting = uiState.isSubmittingAction,
                        onConfirm = { onConfirmBarrier(barrier.id) },
                        onDismiss = { onDismissBarrier(barrier.id) },
                        onRefute = { onRefuteBarrier(barrier.id) }
                    )
                }
            }

            // =================================================================
            // SECTION D: Data Quality & Epistemic Audit
            // =================================================================
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DataQualitySection(
                    availability = uiState.effectiveAvailability,
                    isUsageAccessPermissionGranted = uiState.isUsageAccessPermissionGranted
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// =============================================================================
// Top Date Selector & Usage Metric Composables
// =============================================================================

@Composable
private fun DateSelectorHeader(
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onSelectDate(selectedDate.minusDays(1)) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Day"
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        selectedDate == today -> "Today"
                        selectedDate == today.minusDays(1) -> "Yesterday"
                        else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedDate != today) {
                    TextButton(onClick = { onSelectDate(today) }) {
                        Text("Today")
                    }
                }
                IconButton(
                    onClick = { onSelectDate(selectedDate.plusDays(1)) },
                    enabled = selectedDate < today
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Day"
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(
    context: Context,
    onRecheck: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Permission Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Usage Access Permission Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "IronMind requires Android Usage Access to measure app usage duration on this device. Without permission, screen time cannot be observed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        openUsageAccessSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Grant Usage Access")
                }
            }
        }
    }
}

@Composable
private fun SettingsDisabledCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Settings Disabled",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Observation Disabled",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "App usage observation is disabled in your IronMind settings. Enable it in Settings to resume screen time metrics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun DailyUsageSummaryCard(
    dailyUsage: DailyAppUsageIntelligence?,
    availability: SourceAvailability
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DAILY USAGE SUMMARY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))

            val totalDurationMs = dailyUsage?.totalObservedDurationMs
            if (totalDurationMs != null) {
                Text(
                    text = formatDuration(totalDurationMs),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = "Not Available",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Availability status chip / text
            Text(
                text = when (availability) {
                    SourceAvailability.AVAILABLE -> when (dailyUsage?.sourceProvenance) {
                        UsageSourceProvenance.ROOM_PERSISTED_OBSERVATIONS -> "Cumulative app usage (persisted history)"
                        else -> "Cumulative app usage"
                    }
                    SourceAvailability.EMPTY_NO_DATA -> "No device activity recorded for this date"
                    SourceAvailability.PARTIAL_HISTORY -> "Partial observation history"
                    SourceAvailability.PERMISSION_DENIED -> "Permission denied: Duration unavailable"
                    SourceAvailability.PERMISSION_NOT_REQUESTED -> "Observation disabled: Duration unavailable"
                    SourceAvailability.UNAVAILABLE_HARDWARE -> "Hardware stats unavailable"
                    SourceAvailability.STALE_DATA -> "Stale data cached"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            // Day-over-day comparison
            val comparison = dailyUsage?.comparisonWithPreviousDay
            if (comparison != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                val deltaFormatted = formatDuration(kotlin.math.abs(comparison.durationDeltaMs))
                val sign = if (comparison.durationDeltaMs > 0) "+" else if (comparison.durationDeltaMs < 0) "-" else ""
                val percentageText = comparison.percentageDelta?.let {
                    val pctSign = if (it > 0) "+" else ""
                    " ($pctSign${String.format(Locale.US, "%.1f", it)}%)"
                } ?: ""

                Text(
                    text = if (comparison.durationDeltaMs == 0L) {
                        "Identical usage compared to previous day"
                    } else {
                        "$sign$deltaFormatted$percentageText compared to previous day"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (comparison.comparisonNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = comparison.comparisonNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusProtectionSummaryCard(
    activeSessions: List<ProtectionSession>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "FOCUS & APP PROTECTION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Protected focus sessions are measured independently from observed app screen time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (activeSessions.isNotEmpty()) {
                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                activeSessions.forEach { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Active Protection Session",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = session.status.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Started at: ${timeFormat.format(Date(session.startedAt))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (session.scheduledEndAt != null) {
                                Text(
                                    text = "Scheduled end: ${timeFormat.format(Date(session.scheduledEndAt))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No active protection session. Focus boundaries are not currently engaged.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUsageListItem(
    item: AppUsageItem
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.appName ?: item.packageName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.appName != null && item.appName != item.packageName) {
                    Text(
                        text = item.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", item.percentageOfTotal)}% of total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${item.sessionCount} sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = formatDuration(item.totalDurationMs),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyUsageCard(availability: SourceAvailability) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (availability) {
                    SourceAvailability.PERMISSION_DENIED -> "Usage Access permission required to observe applications."
                    SourceAvailability.PERMISSION_NOT_REQUESTED -> "Observation disabled. Enable in Settings."
                    SourceAvailability.PARTIAL_HISTORY -> "Collection history is incomplete for this interval."
                    SourceAvailability.STALE_DATA -> "Usage data is outdated. Pull to refresh."
                    else -> "No application usage recorded for this date."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================================
// Section A: Behavioural Patterns Composables
// =============================================================================

@Composable
private fun EmptyPatternCard(availability: SourceAvailability) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (availability == SourceAvailability.PERMISSION_DENIED) {
                    "Behaviour patterns require Usage Access permission to observe digital sessions."
                } else {
                    "No behavioural patterns discovered yet. Patterns will emerge as you use your device and complete focus sessions."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PatternItemCard(
    pattern: Pattern,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRefute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Category badge
                    pattern.category?.let { cat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = cat.name.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Lifecycle state badge
                    val stateColor = when (pattern.lifecycleState) {
                        PatternLifecycleState.ESTABLISHED -> Color(0xFF2E7D32)
                        PatternLifecycleState.EMERGING -> MaterialTheme.colorScheme.primary
                        PatternLifecycleState.CANDIDATE -> MaterialTheme.colorScheme.tertiary
                        PatternLifecycleState.WEAKENING,
                        PatternLifecycleState.INACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(stateColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = pattern.lifecycleState.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = stateColor
                        )
                    }
                }

                // Confirmation state badge
                Text(
                    text = when (pattern.confirmationState) {
                        MemoryConfirmationState.USER_CONFIRMED -> "CONFIRMED"
                        MemoryConfirmationState.SYSTEM_CONFIRMED -> "AUTO-CONFIRMED"
                        MemoryConfirmationState.USER_REJECTED -> "REJECTED"
                        MemoryConfirmationState.UNCONFIRMED -> "UNCONFIRMED"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (pattern.confirmationState) {
                        MemoryConfirmationState.USER_CONFIRMED,
                        MemoryConfirmationState.SYSTEM_CONFIRMED -> Color(0xFF2E7D32)
                        MemoryConfirmationState.USER_REJECTED -> MaterialTheme.colorScheme.error
                        MemoryConfirmationState.UNCONFIRMED -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = pattern.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // Cross-domain disclaimer
            if (pattern.category == PatternCategory.CROSS_DOMAIN || pattern.predictedBehavior != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pattern.predictedBehavior ?: "Note: Observed correlation does not imply causation.",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Confidence & evidence metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${pattern.evidenceCount} observation(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Confidence: ${String.format(Locale.US, "%.0f", pattern.confidence * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Action Buttons
            if (pattern.confirmationState == MemoryConfirmationState.UNCONFIRMED) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirm,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Confirm")
                    }

                    OutlinedButton(
                        onClick = onRefute,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Refute", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refute")
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

// =============================================================================
// Section B: Personal Insights Composables
// =============================================================================

@Composable
private fun EmptyInsightCard(availability: SourceAvailability) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (availability == SourceAvailability.PERMISSION_DENIED) {
                    "Personal insights require Usage Access permission to evaluate daily focus patterns."
                } else {
                    "No personal insights available yet. Insights are generated from observed patterns and focus sessions."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PersonalInsightItemCard(
    insight: PersonalInsight,
    isSubmitting: Boolean,
    onMarkUseful: () -> Unit,
    onDismiss: () -> Unit,
    onReviewLater: () -> Unit
) {
    var expandedDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Epistemic Authority Badge
                    val (authorityLabel, authorityBg) = when (insight.epistemicAuthority) {
                        EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION -> "Observed Fact" to Color(0xFF2E7D32).copy(alpha = 0.15f)
                        EpistemicAuthority.DERIVED_CALCULATION -> "Derived Metric" to MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        EpistemicAuthority.AI_HYPOTHESIS -> "AI Hypothesis" to MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        EpistemicAuthority.USER_CONFIRMED_FACT -> "Confirmed Fact" to Color(0xFF1B5E20).copy(alpha = 0.2f)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(authorityBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = authorityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Source Freshness Badge
                    if (insight.sourceFreshness == SourceFreshness.STALE) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "STALE DATA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Review Status badge
                Text(
                    text = insight.reviewStatus.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (insight.reviewStatus) {
                        InsightReviewStatus.USEFUL -> Color(0xFF2E7D32)
                        InsightReviewStatus.DISMISSED -> MaterialTheme.colorScheme.error
                        InsightReviewStatus.REVIEW_LATER -> MaterialTheme.colorScheme.tertiary
                        InsightReviewStatus.UNREVIEWED -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Explanation
            Text(
                text = insight.explanation,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Observation period and confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Period: ${insight.explainabilityBlock.observationPeriod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Confidence: ${String.format(Locale.US, "%.0f", insight.confidence * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Expandable Explainability Accordion
            TextButton(
                onClick = { expandedDetails = !expandedDetails },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (expandedDetails) "Hide Details ▲" else "Explain This Insight ▼")
            }

            AnimatedVisibility(visible = expandedDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "What was observed:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = insight.explainabilityBlock.whatWasObserved,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Supporting Evidence:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = insight.explainabilityBlock.supportingEvidenceDescription,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Why it matters:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = insight.explainabilityBlock.whyItMatters,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Uncertainty & Limitations:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = insight.explainabilityBlock.uncertaintyOrLimitations,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Suggested Next Steps:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = insight.explainabilityBlock.suggestedNextSteps,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (insight.sourceProvenance.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Source Provenance: ${insight.sourceProvenance.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Review Action Buttons
            if (insight.reviewStatus == InsightReviewStatus.UNREVIEWED || insight.reviewStatus == InsightReviewStatus.REVIEW_LATER) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onMarkUseful,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Useful", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Useful")
                    }

                    OutlinedButton(
                        onClick = onReviewLater,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Later")
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

// =============================================================================
// Section C: Barrier Hypotheses Composables
// =============================================================================

@Composable
private fun EmptyBarrierCard(availability: SourceAvailability) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (availability == SourceAvailability.PERMISSION_DENIED) {
                    "Barrier identification requires Usage Access permission to detect digital distraction points."
                } else {
                    "No barrier hypotheses identified yet. Barriers require evidence across multiple calendar days."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BarrierHypothesisItemCard(
    barrier: BarrierHypothesis,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRefute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Unconfirmed Banner if applicable
            if (barrier.confirmationState == BarrierConfirmationState.UNCONFIRMED) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Unconfirmed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Hypothesis — Unconfirmed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = barrier.category.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Confirmation State
                Text(
                    text = barrier.confirmationState.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (barrier.confirmationState) {
                        BarrierConfirmationState.CONFIRMED -> Color(0xFF2E7D32)
                        BarrierConfirmationState.REJECTED -> MaterialTheme.colorScheme.error
                        BarrierConfirmationState.DISMISSED -> MaterialTheme.colorScheme.onSurfaceVariant
                        BarrierConfirmationState.UNCONFIRMED -> MaterialTheme.colorScheme.tertiary
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Description
            Text(
                text = barrier.title.ifBlank { barrier.category.name.replace("_", " ") },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = barrier.description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${barrier.evidenceCount} observation(s) across ${barrier.distinctDays} day(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Confidence: ${String.format(Locale.US, "%.0f", barrier.confidence * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Actions
            if (barrier.confirmationState == BarrierConfirmationState.UNCONFIRMED) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirm,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Confirm")
                    }

                    OutlinedButton(
                        onClick = onRefute,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Refute", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refute")
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

// =============================================================================
// Section D: Data Quality & Epistemic Audit Composable
// =============================================================================

@Composable
private fun DataQualitySection(
    availability: SourceAvailability,
    isUsageAccessPermissionGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Data Quality",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Data Quality & Epistemic Safeguards",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Availability: ${availability.name} • Permission: ${if (isUsageAccessPermissionGranted) "Granted" else "Denied"}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "IronMind never treats missing usage access as zero usage. All discovered behaviour patterns and personal insights are strictly evidence-backed and require explicit confirmation for barrier establishment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================================
// Helper Error and Action Banner Composables
// =============================================================================

@Composable
private fun ActionErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ObservationNotesCard(note: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Observation Notes",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Retry", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0m"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun openUsageAccessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:" + context.packageName)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e2: Exception) {
            // Cannot open settings intent
        }
    }
}
