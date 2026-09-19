package com.sanket_satpute_20.ironmind.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase.Action

/**
 * Sprint V2.11: Suggestion-Only Intelligence UI
 * 
 * Displays a typed AI intervention recommendation without enforcing it.
 * The user maintains full control and must explicitly choose an outcome.
 */
@Composable
fun InterventionSuggestionCard(
    recommendation: AIOutput.InterventionRecommendation,
    onAction: (Action, String?) -> Unit,
    modifier: Modifier = Modifier
) {
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
                text = recommendation.supportingContext ?: recommendation.reason,
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
                text = recommendation.recommendation,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (recommendation.supportingContext != null) {
                Text(
                    text = "Why:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = recommendation.reason,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Interactive Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { onAction(Action.IGNORE, null) }) {
                    Text("Ignore")
                }
                
                Row {
                    TextButton(onClick = { onAction(Action.CORRECT, "User marked as corrected") }) {
                        Text("Correct")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onAction(Action.REJECT, null) }) {
                        Text("Reject")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onAction(Action.ACCEPT, null) }) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}
