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

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsScreenContent(
        modifier = modifier,
        uiState = uiState,
        onGlobalPauseToggle = { isPaused -> viewModel.setGlobalPause(isPaused) },
        onAppUsageObservationToggle = { isEnabled -> viewModel.setAppUsageObservationEnabled(isEnabled) },
        onNotificationObservationToggle = { isEnabled -> viewModel.setNotificationObservationEnabled(isEnabled) }
    )
}

@Composable
fun SettingsScreenContent(
    modifier: Modifier = Modifier,
    uiState: SettingsUiState,
    onGlobalPauseToggle: (Boolean) -> Unit,
    onAppUsageObservationToggle: (Boolean) -> Unit = {},
    onNotificationObservationToggle: (Boolean) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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
                    text = "🛑 Emergency Stop — Pause All Autonomy",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "When enabled, IronMind will stop all autonomous background actions. " +
                            "You can re-enable autonomy at any time.",
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
                            text = if (uiState.isGlobalPauseActive) "Autonomy PAUSED" else "Autonomy ACTIVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (uiState.isGlobalPauseActive)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Switch(
                            checked = uiState.isGlobalPauseActive,
                            onCheckedChange = { onGlobalPauseToggle(it) }
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
    }
}
