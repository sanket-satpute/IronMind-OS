package com.sanket_satpute_20.ironmind.ui.screens.protection.restriction

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfig
import com.sanket_satpute_20.ironmind.domain.provider.DiscoveredApp

@Composable
fun AppRestrictionSection(
    modifier: Modifier = Modifier,
    uiState: AppRestrictionUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSaveSelectedRestrictions: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleRestrictionEnabled: (String, Boolean) -> Unit,
    onEmergencyOverride: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search installed applications...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        if (uiState.isLoading && uiState.installedApps.isEmpty() && uiState.configuredRestrictions.isEmpty()) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Error banner if any
            if (uiState.errorMessage != null) {
                item {
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
                                text = uiState.errorMessage,
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
            }

            // Emergency Override Card when active enforcement is running
            if (uiState.isProtectionActive) {
                item {
                    ActiveEnforcementOverrideCard(
                        enforcedCount = uiState.activeEnforcedPackageNames.size,
                        onEmergencyOverride = onEmergencyOverride
                    )
                }
            }

            // Accessibility permission notice
            if (!uiState.isAccessibilityPermissionGranted) {
                item {
                    AccessibilityNoticeCard(context = context)
                }
            }

            // Batch selection action bar if apps are selected
            if (uiState.selectedPackageNames.isNotEmpty()) {
                item {
                    SelectionActionBar(
                        selectedCount = uiState.selectedPackageNames.size,
                        onSave = onSaveSelectedRestrictions,
                        onClear = onClearSelection
                    )
                }
            }

            // Section 1: Configured Restrictions
            item {
                Text(
                    text = "Configured Restrictions (${uiState.configuredRestrictions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.configuredRestrictions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "No applications are currently configured for restriction. Select applications from the list below to configure them.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.configuredRestrictions, key = { it.id }) { config ->
                    ConfiguredRestrictionItem(
                        config = config,
                        isActivelyEnforced = uiState.activeEnforcedPackageNames.contains(config.packageName),
                        isUninstalled = uiState.uninstalledConfiguredPackages.contains(config.packageName),
                        onToggle = { isEnabled -> onToggleRestrictionEnabled(config.id, isEnabled) }
                    )
                }
            }

            // Section 2: Installed Applications Browser
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Installed Applications (${uiState.installedApps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select applications to configure focus restrictions. Selecting an app does not block it until actively enforced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.installedApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty()) {
                                "No applications match '${uiState.searchQuery}'."
                            } else {
                                "No launchable applications discovered on this device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.installedApps, key = { it.packageName }) { app ->
                    val isConfigured = uiState.configuredPackages.contains(app.packageName)
                    val isSelected = uiState.selectedPackageNames.contains(app.packageName)
                    val isActivelyEnforced = uiState.activeEnforcedPackageNames.contains(app.packageName)

                    InstalledAppPickerItem(
                        app = app,
                        isSelected = isSelected,
                        isConfigured = isConfigured,
                        isActivelyEnforced = isActivelyEnforced,
                        onToggleSelection = { onToggleAppSelection(app.packageName) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ActiveEnforcementOverrideCard(
    enforcedCount: Int,
    onEmergencyOverride: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Active Enforcement",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Active Restriction Enforcement in Progress",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "IronMind is actively enforcing restrictions on $enforcedCount application(s). Opening restricted apps returns you to the home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onEmergencyOverride,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Emergency Override")
                }
            }
        }
    }
}

@Composable
private fun ConfiguredRestrictionItem(
    config: AppRestrictionConfig,
    isActivelyEnforced: Boolean,
    isUninstalled: Boolean,
    onToggle: (Boolean) -> Unit
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
                    text = config.appDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = config.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isUninstalled -> {
                            AssistChip(
                                onClick = {},
                                label = { Text("Uninstalled from device") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }
                        isActivelyEnforced -> {
                            AssistChip(
                                onClick = {},
                                label = { Text("Actively Enforced") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        config.isEnabled -> {
                            AssistChip(
                                onClick = {},
                                label = { Text("Configured (Enabled)") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                        else -> {
                            AssistChip(
                                onClick = {},
                                label = { Text("Configured (Disabled)") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            Switch(
                checked = config.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun InstalledAppPickerItem(
    app: DiscoveredApp,
    isSelected: Boolean,
    isConfigured: Boolean,
    isActivelyEnforced: Boolean,
    onToggleSelection: () -> Unit
) {
    Card(
        onClick = onToggleSelection,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
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
                    text = app.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isConfigured || isActivelyEnforced) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isActivelyEnforced) "• Actively enforced in session" else "• Already configured",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActivelyEnforced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() }
            )
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$selectedCount selected for restriction",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
                Button(onClick = onSave) {
                    Text("Add Restrictions")
                }
            }
        }
    }
}

@Composable
private fun AccessibilityNoticeCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Accessibility Notice",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Accessibility Service Notice",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Restrictions can be configured now. However, active focus enforcement requires the IronMind Accessibility Service to return to the home screen when restricted apps are launched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (e: Exception) {
                            // Cannot open settings
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Accessibility Settings")
                }
            }
        }
    }
}
