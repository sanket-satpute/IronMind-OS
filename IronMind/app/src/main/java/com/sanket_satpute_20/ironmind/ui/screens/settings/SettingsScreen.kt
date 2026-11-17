package com.sanket_satpute_20.ironmind.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    onNavigateToDevControlCenter: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    SettingsScreenContent(
        modifier = modifier,
        uiState = uiState,
        onGlobalPauseToggle = { isPaused -> viewModel.setGlobalPause(isPaused) },
        onAppUsageObservationToggle = { isEnabled -> viewModel.setAppUsageObservationEnabled(isEnabled) },
        onNotificationObservationToggle = { isEnabled -> viewModel.setNotificationObservationEnabled(isEnabled) },
        onCalendarObservationToggle = { isEnabled -> viewModel.setCalendarObservationEnabled(isEnabled) },
        onLocationObservationToggle = { isEnabled -> viewModel.setLocationObservationEnabled(isEnabled) },
        onActivityObservationToggle = { isEnabled -> viewModel.setActivityObservationEnabled(isEnabled) },
        onExportDataClick = { viewModel.exportUserData() },
        onShareExportClick = {
            val shareIntent = viewModel.getShareIntent(context)
            if (shareIntent != null) {
                context.startActivity(shareIntent)
            }
        },
        onDismissExportMessage = { viewModel.dismissExportMessages() },
        onEraseLocalDataClick = { viewModel.requestErasureConfirmation() },
        onConfirmErasure = { viewModel.eraseLocalData() },
        onDismissErasureConfirmation = { viewModel.dismissErasureConfirmation() },
        onDismissErasureMessage = { viewModel.dismissErasureMessages() },
        onNavigateToDevControlCenter = onNavigateToDevControlCenter
    )
}

@Composable
fun SettingsScreenContent(
    modifier: Modifier = Modifier,
    uiState: SettingsUiState,
    onGlobalPauseToggle: (Boolean) -> Unit,
    onAppUsageObservationToggle: (Boolean) -> Unit = {},
    onNotificationObservationToggle: (Boolean) -> Unit = {},
    onCalendarObservationToggle: (Boolean) -> Unit = {},
    onLocationObservationToggle: (Boolean) -> Unit = {},
    onActivityObservationToggle: (Boolean) -> Unit = {},
    onExportDataClick: () -> Unit = {},
    onShareExportClick: () -> Unit = {},
    onDismissExportMessage: () -> Unit = {},
    onEraseLocalDataClick: () -> Unit = {},
    onConfirmErasure: () -> Unit = {},
    onDismissErasureConfirmation: () -> Unit = {},
    onDismissErasureMessage: () -> Unit = {},
    onNavigateToDevControlCenter: () -> Unit = {}
) {
    if (uiState.isErasureConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onDismissErasureConfirmation,
            title = {
                Text(
                    text = "Erase Local IronMind Data?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete all local data on this device?\n\n" +
                            "• This will erase your local goals, plans, tasks, reflections, memories, observations, and protection settings.\n" +
                            "• All scheduled local background work will be cancelled immediately.\n\n" +
                            "IMPORTANT: This action only erases local data stored on this device. " +
                            "It does NOT delete remote Firestore data or delete your Firebase Authentication account.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmErasure,
                    enabled = !uiState.isErasingData,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (uiState.isErasingData) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Erasing...")
                    } else {
                        Text("Erase Local Data")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissErasureConfirmation,
                    enabled = !uiState.isErasingData
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ── Emergency stop card ──────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isGlobalPauseActive)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pause Background Observation",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Temporarily stop collecting background context such as app usage and notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.isGlobalPauseActive) "Observation PAUSED" else "Observation ACTIVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (uiState.isGlobalPauseActive)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Switch(
                            checked = uiState.isGlobalPauseActive,
                            onCheckedChange = { onGlobalPauseToggle(it) },
                            modifier = Modifier.semantics {
                                contentDescription = if (uiState.isGlobalPauseActive) "Resume Observation" else "Pause Observation"
                            }
                        )
                    }
                }
                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ── App Usage Observation card ───────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📊 App Usage Observation",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Allow IronMind to observe which apps you use and for how long. " +
                            "This is used as behavioral context — not for surveillance. " +
                            "Only timing and package names are recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (!uiState.isAppUsagePermissionGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "⚠ Permission required: Go to Settings → Apps → Special app access → Usage access and grant access to IronMind.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isAppUsageObservationEnabled) "Observation ON" else "Observation OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.isAppUsageObservationEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.isAppUsageObservationEnabled,
                        onCheckedChange = { onAppUsageObservationToggle(it) }
                    )
                }
            }
        }

        // ── Notification Observation card ───────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔔 Notification Observation",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Allow IronMind to observe incoming notifications to understand interruption conditions. " +
                            "Only metadata is recorded (app name, timing) to avoid noisy notifications. " +
                            "This is not used for surveillance.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (!uiState.isNotificationPermissionGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "⚠ Permission required: Go to Settings → Apps → Special app access → Device & app notifications and grant access to IronMind.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isNotificationObservationEnabled) "Observation ON" else "Observation OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.isNotificationObservationEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.isNotificationObservationEnabled,
                        onCheckedChange = { onNotificationObservationToggle(it) }
                    )
                }
            }
        }

        // ── Calendar Observation card ───────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📅 Calendar Context",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Allow IronMind to observe your calendar for contextual awareness (upcoming events, free time). " +
                            "This helps IronMind reason about time reality rather than scheduling into fictional availability.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (!uiState.isCalendarPermissionGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "⚠ Permission required: Go to App Info → Permissions and grant Calendar access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isCalendarObservationEnabled) "Observation ON" else "Observation OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.isCalendarObservationEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.isCalendarObservationEnabled,
                        onCheckedChange = { onCalendarObservationToggle(it) }
                    )
                }
            }
        }

        // ── Location Observation card ───────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📍 Location Context",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Allow IronMind to observe your coarse location. " +
                            "This is a single approximate snapshot (not continuous GPS tracking) to provide environment awareness.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (!uiState.isLocationPermissionGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "⚠ Permission required: Go to App Info → Permissions and grant Location access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isLocationObservationEnabled) "Observation ON" else "Observation OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.isLocationObservationEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.isLocationObservationEnabled,
                        onCheckedChange = { onLocationObservationToggle(it) }
                    )
                }
            }
        }

        // ── Activity Observation card ───────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🏃 Activity Context",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Allow IronMind to observe broad physical activity (e.g., walking, in-vehicle) to " +
                            "improve contextual awareness. No health data or exact diagnostics are stored.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (!uiState.isActivityPermissionGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "⚠ Permission required: Go to App Info → Permissions and grant Physical Activity access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isActivityObservationEnabled) "Observation ON" else "Observation OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.isActivityObservationEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.isActivityObservationEnabled,
                        onCheckedChange = { onActivityObservationToggle(it) }
                    )
                }
            }
        }

        // ── User Data Export ────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📥 Export My Data",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Generate a complete, private JSON archive of all your personal data " +
                            "stored on this device. The export is created locally and can be shared " +
                            "via the standard Android share sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Export Success Banner
                uiState.exportSuccessMetadata?.let { metadata ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✓ Export Ready",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TextButton(onClick = onDismissExportMessage) {
                                    Text("Dismiss", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "File: ${metadata.fileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Size: ${formatFileSize(metadata.sizeBytes)} • Records: ${metadata.recordCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onShareExportClick,
                                enabled = !uiState.isExporting && !uiState.isErasingData,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Share Export")
                            }
                        }
                    }
                }

                // Export Error Banner
                uiState.exportErrorMessage?.let { errorMsg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠ $errorMsg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissExportMessage) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                Button(
                    onClick = onExportDataClick,
                    enabled = !uiState.isExporting && !uiState.isErasingData,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exporting Data...")
                    } else {
                        Text("Export My Data")
                    }
                }
            }
        }

        // ── Local Data Management (Erasure) ──────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🗑 Local Data Management",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Permanently erase all locally stored data from this device, including goals, " +
                            "plans, tasks, reflections, observations, patterns, and protection rules. " +
                            "In-flight background work will be cancelled.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Notice: This action only erases data stored on this device. It does NOT delete " +
                            "remote Firestore data or delete your Firebase Authentication account.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Success Feedback Banner
                uiState.erasureSuccessMessage?.let { successMsg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✓ $successMsg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissErasureMessage) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }

                // Error Feedback Banner
                uiState.erasureErrorMessage?.let { errorMsg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠ $errorMsg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissErasureMessage) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                Button(
                    onClick = onEraseLocalDataClick,
                    enabled = !uiState.isErasingData,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (uiState.isErasingData) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Erasing Local Data...")
                    } else {
                        Text("Erase Local IronMind Data")
                    }
                }
            }
        }

        // ── Development Control Center ─────────────────────────────────────────
        val context = androidx.compose.ui.platform.LocalContext.current
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Button(
                onClick = onNavigateToDevControlCenter,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("🛠 Development Control Center")
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
