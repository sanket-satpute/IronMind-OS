package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.model.TimelineItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
    intelligenceViewModel: IntelligenceHistoryViewModel = viewModel(factory = IntelligenceHistoryViewModel.Factory),
    correctionViewModel: CorrectionReviewViewModel = viewModel(factory = CorrectionReviewViewModel.Factory),
    patternViewModel: PatternReviewViewModel = viewModel(factory = PatternReviewViewModel.Factory),
    barrierViewModel: BarrierReviewViewModel = viewModel(factory = BarrierReviewViewModel.Factory),
    memoryViewModel: MemoryManagementViewModel = viewModel(factory = MemoryManagementViewModel.Factory),
    insightViewModel: InsightReviewViewModel = viewModel(factory = InsightReviewViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val intelligenceUiState by intelligenceViewModel.uiState.collectAsState()
    val correctionUiState by correctionViewModel.uiState.collectAsState()
    val patternUiState by patternViewModel.uiState.collectAsState()
    val barrierUiState by barrierViewModel.uiState.collectAsState()
    val memoryUiState by memoryViewModel.uiState.collectAsState()
    val insightUiState by insightViewModel.uiState.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Timeline", "Intelligence", "Corrections", "Patterns", "Barriers", "Memories", "Insights")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> {
                when (val state = uiState) {
                    is HistoryUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is HistoryUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is HistoryUiState.Success -> {
                        if (state.timelineItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "No history available.")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                items(state.timelineItems) { item ->
                                    TimelineItemCard(item = item)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                IntelligenceHistoryContent(uiState = intelligenceUiState)
            }
            2 -> {
                CorrectionReviewContent(
                    uiState = correctionUiState,
                    onApprove = { correctionViewModel.approve(it) },
                    onRefute = { correctionViewModel.refute(it) },
                    onDismiss = { correctionViewModel.dismiss(it) }
                )
            }
            3 -> {
                PatternReviewContent(
                    uiState = patternUiState,
                    onConfirm = { patternViewModel.confirm(it) },
                    onRefute = { patternViewModel.refute(it) },
                    onDismiss = { patternViewModel.dismiss(it) }
                )
            }
            4 -> {
                BarrierReviewContent(
                    uiState = barrierUiState,
                    onConfirm = { barrierViewModel.confirm(it) },
                    onRefute = { barrierViewModel.refute(it) },
                    onDismiss = { barrierViewModel.dismiss(it) }
                )
            }
            5 -> {
                MemoryManagementContent(
                    uiState = memoryUiState,
                    onConfirm = { memoryViewModel.confirm(it) },
                    onCorrect = { id, content -> memoryViewModel.correct(id, content) },
                    onReject = { memoryViewModel.reject(it) },
                    onDelete = { memoryViewModel.delete(it) }
                )
            }
            6 -> {
                InsightReviewContent(
                    uiState = insightUiState,
                    onMarkUseful = { insightViewModel.markUseful(it) },
                    onDismiss = { insightViewModel.dismiss(it) },
                    onReviewLater = { insightViewModel.reviewLater(it) },
                    onFeedback = { id, text -> insightViewModel.provideFeedback(id, text) },
                    onAcceptRecommendation = { insightViewModel.acceptRecommendation(it) },
                    onRejectRecommendation = { insightViewModel.rejectRecommendation(it) },
                    onAnalyzeNow = { insightViewModel.runIntelligenceOrchestration() },
                    onConfirmFact = { insightViewModel.confirmFactualClaim(it) },
                    onRefuteFact = { id, text -> insightViewModel.refuteFactualClaim(id, text) }
                )
            }
        }
    }
}

@Composable
fun TimelineItemCard(item: TimelineItem) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(item.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon based on type
            val icon = when (item) {
                is TimelineItem.CommitmentEvent -> Icons.Default.CheckCircle
                is TimelineItem.ReflectionRecorded -> Icons.Default.Star
                is TimelineItem.GoalEvent -> Icons.Default.Star
                is TimelineItem.PlanEvent -> Icons.Default.Info
                is TimelineItem.TaskEvent -> Icons.Default.CheckCircle
                is TimelineItem.OutcomeRecorded -> Icons.Default.CheckCircle
                is TimelineItem.MajorEvent -> Icons.Default.Info
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (item) {
                    is TimelineItem.CommitmentEvent -> {
                        Text(
                            text = item.actionDescription,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = item.commitmentTitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    is TimelineItem.ReflectionRecorded -> {
                        Text(
                            text = "Reflection Recorded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Sentiment: ${item.sentiment}", style = MaterialTheme.typography.bodyMedium)
                    }
                    is TimelineItem.GoalEvent -> {
                        Text(
                            text = item.actionDescription,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = item.goalTitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    is TimelineItem.PlanEvent -> {
                        Text(
                            text = item.actionDescription,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = item.planTitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    is TimelineItem.TaskEvent -> {
                        Text(
                            text = item.actionDescription,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = item.taskTitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    is TimelineItem.OutcomeRecorded -> {
                        Text(
                            text = item.actionDescription,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is TimelineItem.MajorEvent -> {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
