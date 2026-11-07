package com.sanket_satpute_20.ironmind.ui.screens.digital

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.digital.AppUsageItem
import com.sanket_satpute_20.ironmind.domain.model.digital.DailyAppUsageIntelligence
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
                title = { Text("Digital Behaviour") },
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
            onAcknowledgeError = { viewModel.acknowledgeError() }
        )
    }
}

@Composable
fun DigitalBehaviourContent(
    modifier: Modifier = Modifier,
    uiState: DigitalBehaviourUiState,
    onSelectDate: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onAcknowledgeError: () -> Unit
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
            // Error banner if any
            if (uiState.errorMessage != null) {
                item {
                    ErrorBanner(
                        message = uiState.errorMessage,
                        onRetry = onAcknowledgeError
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

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

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
                    SourceAvailability.AVAILABLE -> "Observed screen time"
                    SourceAvailability.EMPTY_NO_DATA -> "No device activity recorded for this date"
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
                    else -> "No application usage recorded for this date."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
