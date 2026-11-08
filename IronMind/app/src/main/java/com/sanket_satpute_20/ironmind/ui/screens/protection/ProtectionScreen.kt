package com.sanket_satpute_20.ironmind.ui.screens.protection

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.sanket_satpute_20.ironmind.domain.model.EnforcementAvailability
import com.sanket_satpute_20.ironmind.ui.screens.digital.DigitalBehaviourContent
import com.sanket_satpute_20.ironmind.ui.screens.digital.DigitalBehaviourViewModel
import com.sanket_satpute_20.ironmind.ui.screens.protection.restriction.AppRestrictionSection
import com.sanket_satpute_20.ironmind.ui.screens.protection.restriction.AppRestrictionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionScreen(
    modifier: Modifier = Modifier,
    viewModel: ProtectionViewModel = viewModel(factory = ProtectionViewModel.Factory),
    digitalViewModel: DigitalBehaviourViewModel = viewModel(factory = DigitalBehaviourViewModel.Factory),
    restrictionViewModel: AppRestrictionViewModel = viewModel(factory = AppRestrictionViewModel.Factory),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val digitalUiState by digitalViewModel.uiState.collectAsState()
    val restrictionUiState by restrictionViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Digital Behaviour", "App Restrictions", "Focus Protection")

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadCurrentSession()
                digitalViewModel.refresh()
                restrictionViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Common distracting packages for V0 presentation
    val commonPackages = listOf(
        "com.google.android.youtube" to "YouTube",
        "com.instagram.android" to "Instagram",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.twitter.android" to "X (Twitter)",
        "com.facebook.katana" to "Facebook"
    )

    val selectedPackages = remember { mutableStateListOf<String>() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Digital Behaviour & Protection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTabIndex) {
                    0 -> {
                        DigitalBehaviourContent(
                            uiState = digitalUiState,
                            onSelectDate = { digitalViewModel.selectDate(it) },
                            onRefresh = { digitalViewModel.refresh() },
                            onAcknowledgeError = { digitalViewModel.acknowledgeError() }
                        )
                    }
                    1 -> {
                        AppRestrictionSection(
                            uiState = restrictionUiState,
                            onSearchQueryChange = { restrictionViewModel.updateSearchQuery(it) },
                            onToggleAppSelection = { restrictionViewModel.toggleAppSelection(it) },
                            onSaveSelectedRestrictions = { restrictionViewModel.saveSelectedRestrictions() },
                            onClearSelection = { restrictionViewModel.clearSelection() },
                            onToggleRestrictionEnabled = { id, enabled -> restrictionViewModel.toggleRestriction(id, enabled) },
                            onEmergencyOverride = {
                                restrictionViewModel.emergencyOverride()
                                viewModel.loadCurrentSession()
                            },
                            onRetry = { restrictionViewModel.acknowledgeError() }
                        )
                    }
                    2 -> {
                        when (val state = uiState) {
                            is ProtectionUiState.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            is ProtectionUiState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.acknowledgeError() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                            is ProtectionUiState.NoActiveSession -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "Start Protection Session",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Select apps to protect your focus. When a protected app is opened, IronMind will immediately return you to the home screen. You must grant the Accessibility permission to enable this.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Quick action: Select All Configured Restrictions
                                    if (restrictionUiState.enabledConfiguredPackages.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = {
                                                selectedPackages.clear()
                                                selectedPackages.addAll(restrictionUiState.enabledConfiguredPackages)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Select All Enabled Restrictions (${restrictionUiState.enabledConfiguredPackages.size})")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    Text(
                                        text = "Targets",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Combine configured enabled restrictions with common packages
                                    val targetOptions = remember(restrictionUiState.configuredRestrictions) {
                                        val fromConfig = restrictionUiState.configuredRestrictions
                                            .filter { it.isEnabled }
                                            .map { it.packageName to it.appDisplayName }
                                        val combined = (fromConfig + commonPackages).distinctBy { it.first }
                                        combined
                                    }

                                    LazyColumn(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(targetOptions) { (pkg, name) ->
                                            val isSelected = selectedPackages.contains(pkg)
                                            Card(
                                                onClick = {
                                                    if (isSelected) selectedPackages.remove(pkg)
                                                    else selectedPackages.add(pkg)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, contentDescription = "Selected")
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.startProtection(selectedPackages.toList()) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = selectedPackages.isNotEmpty()
                                    ) {
                                        Text("Start Protection (${selectedPackages.size} Apps)")
                                    }
                                }
                            }
                            is ProtectionUiState.ActiveSession -> {
                                val cardContainerColor = when (state.enforcementAvailability) {
                                    EnforcementAvailability.AVAILABLE -> MaterialTheme.colorScheme.primaryContainer
                                    EnforcementAvailability.PERMISSION_REQUIRED,
                                    EnforcementAvailability.SERVICE_UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
                                    EnforcementAvailability.RECOVERING -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val cardContentColor = when (state.enforcementAvailability) {
                                    EnforcementAvailability.AVAILABLE -> MaterialTheme.colorScheme.onPrimaryContainer
                                    EnforcementAvailability.PERMISSION_REQUIRED,
                                    EnforcementAvailability.SERVICE_UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
                                    EnforcementAvailability.RECOVERING -> MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = cardContainerColor,
                                            contentColor = cardContentColor
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            when (state.enforcementAvailability) {
                                                EnforcementAvailability.AVAILABLE -> {
                                                    Text(
                                                        text = "Protection is Active",
                                                        style = MaterialTheme.typography.headlineMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "Protected apps will be closed immediately upon opening.",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    if (restrictionUiState.activeEnforcedPackageNames.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Text(
                                                            text = "Actively enforcing ${restrictionUiState.activeEnforcedPackageNames.size} restriction(s)",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                EnforcementAvailability.PERMISSION_REQUIRED -> {
                                                    Text(
                                                        text = "Session Active — Permission Required",
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "IronMind Accessibility Service is disabled. Apps are NOT currently blocked. Re-enable Accessibility in Android Settings to resume enforcement.",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Button(
                                                        onClick = {
                                                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                            }
                                                            context.startActivity(intent)
                                                        }
                                                    ) {
                                                        Text("Open Accessibility Settings")
                                                    }
                                                }
                                                EnforcementAvailability.SERVICE_UNAVAILABLE -> {
                                                    Text(
                                                        text = "Session Active — Service Unavailable",
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "The protection service is currently disconnected. Apps are NOT actively blocked.",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                EnforcementAvailability.RECOVERING -> {
                                                    Text(
                                                        text = "Session Active — Resuming...",
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "Reconnecting protection service and restoring app restrictions...",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.stopProtection() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Stop Protection")
                                        }

                                        Button(
                                            onClick = {
                                                restrictionViewModel.emergencyOverride()
                                                viewModel.loadCurrentSession()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Emergency Override")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Emergency override immediately stops all active restriction enforcement without deleting your configured rules.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
