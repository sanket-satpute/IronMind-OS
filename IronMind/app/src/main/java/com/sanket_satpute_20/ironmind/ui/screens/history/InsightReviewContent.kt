package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight

@Composable
fun InsightReviewContent(
    uiState: InsightReviewUiState,
    onMarkUseful: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onReviewLater: (String) -> Unit,
    onFeedback: (String, String) -> Unit,
    onAcceptRecommendation: (String) -> Unit,
    onRejectRecommendation: (String) -> Unit,
    onAnalyzeNow: () -> Unit,
    onConfirmFact: (String) -> Unit = {},
    onRefuteFact: (String, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is InsightReviewUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is InsightReviewUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        is InsightReviewUiState.Success -> {
            Column(modifier = modifier.fillMaxSize()) {
                // Header with on-demand Analyze action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Personal Insights (${uiState.insights.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onAnalyzeNow,
                        enabled = !uiState.isOrchestrating && !uiState.isSubmitting
                    ) {
                        if (uiState.isOrchestrating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analyzing...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analyze Now")
                        }
                    }
                }

                if (uiState.insights.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No personal insights generated yet.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Insights are synthesized from verified productivity context, patterns, and outcomes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onAnalyzeNow, enabled = !uiState.isOrchestrating) {
                                Text("Run Discovery Pipeline")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.insights, key = { it.id }) { insight ->
                            InsightCard(
                                insight = insight,
                                isSubmitting = uiState.isSubmitting,
                                onMarkUseful = { onMarkUseful(insight.id) },
                                onDismiss = { onDismiss(insight.id) },
                                onReviewLater = { onReviewLater(insight.id) },
                                onFeedback = { feedback -> onFeedback(insight.id, feedback) },
                                onAcceptRecommendation = { onAcceptRecommendation(insight.id) },
                                onRejectRecommendation = { onRejectRecommendation(insight.id) },
                                onConfirmFact = { onConfirmFact(insight.id) },
                                onRefuteFact = { corr -> onRefuteFact(insight.id, corr) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InsightCard(
    insight: PersonalInsight,
    isSubmitting: Boolean,
    onMarkUseful: () -> Unit,
    onDismiss: () -> Unit,
    onReviewLater: () -> Unit,
    onFeedback: (String) -> Unit,
    onAcceptRecommendation: () -> Unit,
    onRejectRecommendation: () -> Unit,
    onConfirmFact: () -> Unit = {},
    onRefuteFact: (String?) -> Unit = {}
) {
    var expandedDetails by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }

    val authorityLabel = when (insight.epistemicAuthority) {
        EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION -> "OBSERVATION"
        EpistemicAuthority.DERIVED_CALCULATION -> "CALCULATION"
        EpistemicAuthority.AI_HYPOTHESIS -> "HYPOTHESIS"
        EpistemicAuthority.USER_CONFIRMED_FACT -> "CONFIRMED FACT"
    }

    val authorityBg = when (insight.epistemicAuthority) {
        EpistemicAuthority.AI_HYPOTHESIS -> MaterialTheme.colorScheme.tertiaryContainer
        EpistemicAuthority.USER_CONFIRMED_FACT -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Category badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = insight.category.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Authority badge
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
                }

                // Review Status badge
                Text(
                    text = insight.reviewStatus.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (insight.reviewStatus) {
                        InsightReviewStatus.USEFUL -> MaterialTheme.colorScheme.primary
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

            // Concise explanation
            Text(
                text = insight.explanation,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    text = "Confidence: ${String.format("%.0f", insight.confidence * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Explainability accordion toggle
            TextButton(
                onClick = { expandedDetails = !expandedDetails },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (expandedDetails) "Hide Details ▲" else "Explain This Insight ▼")
            }

            // Expanded Explainability Block
            AnimatedVisibility(visible = expandedDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
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
                }
            }

            // If user feedback exists
            insight.userFeedback?.let { fb ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your Feedback: $fb",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Linked Recommendation Actions
            if (insight.linkedRecommendationId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAcceptRecommendation,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accept Rec")
                    }
                    OutlinedButton(
                        onClick = onRejectRecommendation,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject Rec")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Review action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onMarkUseful,
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Useful")
                }

                OutlinedButton(
                    onClick = onReviewLater,
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Later")
                }

                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dismiss")
                }
            }

            // Feedback field toggle
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { showFeedbackDialog = !showFeedbackDialog },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Add Feedback / Correction")
            }

            AnimatedVisibility(visible = showFeedbackDialog) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text("Correction or note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onRefuteFact(feedbackText.ifBlank { null })
                                showFeedbackDialog = false
                                feedbackText = ""
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Refute Fact")
                        }
                        Button(
                            onClick = {
                                onConfirmFact()
                                showFeedbackDialog = false
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Confirm Fact")
                        }
                        Button(
                            onClick = {
                                if (feedbackText.isNotBlank()) {
                                    onFeedback(feedbackText)
                                    showFeedbackDialog = false
                                    feedbackText = ""
                                }
                            },
                            enabled = !isSubmitting && feedbackText.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Note")
                        }
                    }
                }
            }
        }
    }
}
