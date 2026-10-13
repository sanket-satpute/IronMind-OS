package com.sanket_satpute_20.ironmind.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase.Action

/**
 * Sprint V2.11: Suggestion-Only Intelligence UI
 * Sprint V2.16-C2: Structured Correction UI Interaction with Scope Selection
 * 
 * Displays a typed AI intervention recommendation without enforcing it.
 * The user maintains full control and must explicitly choose an outcome.
 * When correcting, provides a structured dialog for entering feedback and selecting a correction scope.
 */
@Composable
fun InterventionSuggestionCard(
    recommendation: InterventionRecommendation,
    onAction: (Action, String?, CorrectionScope) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCorrectionDialog by remember { mutableStateOf(false) }

    if (showCorrectionDialog) {
        CorrectionDialog(
            recommendation = recommendation,
            onDismiss = { showCorrectionDialog = false },
            onConfirm = { correctedText, scope ->
                showCorrectionDialog = false
                onAction(Action.CORRECT, correctedText, scope)
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "IronMind noticed:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = recommendation.rationale,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "Possible next step:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = recommendation.suggestedAction,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { onAction(Action.IGNORE, null, CorrectionScope.INTERVENTION_CORRECTION) }) {
                    Text("Ignore")
                }
                
                Row {
                    TextButton(onClick = { showCorrectionDialog = true }) {
                        Text("Correct")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onAction(Action.REJECT, null, CorrectionScope.INTERVENTION_CORRECTION) }) {
                        Text("Reject")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onAction(Action.ACCEPT, null, CorrectionScope.INTERVENTION_CORRECTION) }) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}

private data class ScopeOption(
    val scope: CorrectionScope,
    val title: String,
    val description: String
)

@Composable
fun CorrectionDialog(
    recommendation: InterventionRecommendation,
    onDismiss: () -> Unit,
    onConfirm: (String?, CorrectionScope) -> Unit
) {
    var correctionText by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf(CorrectionScope.INTERVENTION_CORRECTION) }

    val scopeOptions = remember {
        listOf(
            ScopeOption(
                scope = CorrectionScope.INTERVENTION_CORRECTION,
                title = "This suggestion only",
                description = "Feedback applies only to this specific recommendation"
            ),
            ScopeOption(
                scope = CorrectionScope.INFERENCE_CORRECTION,
                title = "Disputed reasoning",
                description = "Notes that the observation or rationale was inaccurate"
            ),
            ScopeOption(
                scope = CorrectionScope.PREFERENCE,
                title = "Suggestion preference",
                description = "Advisory guidance considered in AI suggestions for up to 7 days"
            ),
            ScopeOption(
                scope = CorrectionScope.CONSTRAINT,
                title = "Situational boundary",
                description = "Advisory limit provided to AI prompts for up to 7 days (not hard-blocked)"
            ),
            ScopeOption(
                scope = CorrectionScope.EXPLICIT_GENERAL_RULE,
                title = "General guidance note",
                description = "Advisory context provided to AI prompts for up to 7 days (not a permanent rule)"
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Correct Suggestion",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Suggestion:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = recommendation.suggestedAction,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = correctionText,
                    onValueChange = { correctionText = it },
                    label = { Text("What should IronMind know?") },
                    placeholder = { Text("e.g., Schedule changed, not lack of motivation") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "How does this correction apply?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                scopeOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedScope == option.scope,
                                onClick = { selectedScope = option.scope },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedScope == option.scope,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Note: Corrections are recorded in your history and provided as recent guidance for AI suggestions (up to 7 days). They do not permanently alter system rules or hard-block suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val text = correctionText.trim().ifEmpty { null }
                    onConfirm(text, selectedScope)
                }
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
