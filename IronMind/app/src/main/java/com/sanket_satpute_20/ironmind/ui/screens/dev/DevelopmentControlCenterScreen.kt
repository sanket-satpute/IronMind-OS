package com.sanket_satpute_20.ironmind.ui.screens.dev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DevelopmentControlCenterScreen(
    modifier: Modifier = Modifier,
    viewModel: DevelopmentControlCenterViewModel = viewModel(factory = DevelopmentControlCenterViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🛠 Development Control Center",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "WARNING: This is a debug-only facility. It is not part of the normal user experience.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }

        StatCard(title = "Events", count = uiState.eventCount)
        StatCard(title = "Observations", count = uiState.observationCount)
        StatCard(title = "Memories", count = uiState.memoryCount)
        StatCard(title = "Patterns", count = uiState.patternCount)
        StatCard(title = "Decisions", count = uiState.decisionCount)
        StatCard(title = "Interventions", count = uiState.interventionCount)
        StatCard(title = "Experiments", count = uiState.experimentCount)
        StatCard(title = "Sync Outbox", count = uiState.outboxCount)
        StatCard(title = "Protection Rules", count = uiState.protectionRuleCount)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatCard(title: String, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
