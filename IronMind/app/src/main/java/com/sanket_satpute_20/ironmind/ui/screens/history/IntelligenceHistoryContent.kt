package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanket_satpute_20.ironmind.domain.model.intervention.IntelligenceHistoryItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.IntelligenceHistoryResponse
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IntelligenceHistoryContent(
    uiState: IntelligenceHistoryUiState
) {
    when (uiState) {
        is IntelligenceHistoryUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is IntelligenceHistoryUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is IntelligenceHistoryUiState.Success -> {
            if (uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Your Intelligence History will appear here as you work with IronMind.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(uiState.items) { item ->
                        IntelligenceHistoryItemCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun IntelligenceHistoryItemCard(item: IntelligenceHistoryItem) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(item.recommendationTimestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.suggestedAction,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Why",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.rationale,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (item.effectiveResponse != null) {
                Text(
                    text = "Response",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val responseText = when (item.effectiveResponse) {
                    IntelligenceHistoryResponse.ACCEPTED -> "Accepted"
                    IntelligenceHistoryResponse.REJECTED -> "Rejected"
                    IntelligenceHistoryResponse.IGNORED -> "Ignored"
                    IntelligenceHistoryResponse.CORRECTED -> "Corrected"
                }
                Text(
                    text = responseText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            } else {
                val statusText = when (item.recommendationStatus) {
                    InterventionRecommendationStatus.ACCEPTED -> "Accepted"
                    InterventionRecommendationStatus.REJECTED -> "Rejected"
                    InterventionRecommendationStatus.IGNORED -> "Ignored"
                    InterventionRecommendationStatus.EXPIRED -> "Expired"
                    InterventionRecommendationStatus.PENDING -> "Pending"
                }
                Text(
                    text = "Status: $statusText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.effectiveResponse == IntelligenceHistoryResponse.CORRECTED && !item.correctionText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your correction",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\"${item.correctionText}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
