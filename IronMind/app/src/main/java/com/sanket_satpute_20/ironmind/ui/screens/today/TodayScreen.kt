package com.sanket_satpute_20.ironmind.ui.screens.today

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.ui.components.CommitmentStatusControls
import com.sanket_satpute_20.ironmind.ui.components.InterventionSuggestionCard
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.ui.components.CommitmentStatusControls

@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    TodayScreenContent(
        modifier = modifier,
        uiState = uiState,
        onStatusChange = { id, newStatus, outcome -> viewModel.updateCommitmentStatus(id, newStatus, outcome) },
        onSuggestionAction = { recommendation, action, correctedText -> 
            viewModel.handleSuggestionAction(recommendation, action, correctedText) 
        }
    )
}

@Composable
fun TodayScreenContent(
    modifier: Modifier = Modifier,
    uiState: TodayUiState,
    onStatusChange: (String, CommitmentStatus, ResultStatus?) -> Unit,
    onSuggestionAction: (AIOutput.InterventionRecommendation, HandleInterventionResultUseCase.Action, String?) -> Unit = { _, _, _ -> }
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TodayUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is TodayUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is TodayUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Show a prominent banner if global pause is active
                    if (state.isGlobalPauseActive) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "⏸ IronMind Autonomy is paused. Go to Settings to re-enable.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    TodayContent(
                        commitments = state.activeCommitments,
                        activeSuggestion = state.activeSuggestion,
                        onStatusChange = onStatusChange,
                        onSuggestionAction = onSuggestionAction
                    )
                }
            }
        }
    }
}

@Composable
fun TodayContent(
    commitments: List<Commitment>,
    activeSuggestion: AIOutput.InterventionRecommendation? = null,
    onStatusChange: (String, CommitmentStatus, ResultStatus?) -> Unit,
    onSuggestionAction: (AIOutput.InterventionRecommendation, HandleInterventionResultUseCase.Action, String?) -> Unit = { _, _, _ -> }
) {
    if (commitments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No active commitments for today.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // Determine the next action. Prioritize STARTED over COMMITTED.
    val nextAction = commitments.firstOrNull { it.status == CommitmentStatus.STARTED }
        ?: commitments.firstOrNull { it.status == CommitmentStatus.COMMITTED }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (activeSuggestion != null) {
            item {
                InterventionSuggestionCard(
                    recommendation = activeSuggestion,
                    onAction = { action, correctedText ->
                        onSuggestionAction(activeSuggestion, action, correctedText)
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        item {
            Text(
                text = "Next Action",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        if (nextAction != null) {
            item {
                CommitmentCard(
                    commitment = nextAction,
                    isNextAction = true,
                    onStatusChange = { newStatus, outcome -> onStatusChange(nextAction.id, newStatus, outcome) }
                )
            }
        }
        
        val otherCommitments = commitments.filter { it.id != nextAction?.id }
        if (otherCommitments.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Later Today",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(otherCommitments, key = { it.id }) { commitment ->
                CommitmentCard(
                    commitment = commitment,
                    isNextAction = false,
                    onStatusChange = { newStatus, outcome -> onStatusChange(commitment.id, newStatus, outcome) }
                )
            }
        }
    }
}

@Composable
fun CommitmentCard(
    commitment: Commitment,
    isNextAction: Boolean,
    onStatusChange: (CommitmentStatus, ResultStatus?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isNextAction) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, ShapeDefaults.Medium)
                else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = commitment.title, style = MaterialTheme.typography.titleLarge)
            
            if (commitment.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = commitment.description, style = MaterialTheme.typography.bodyMedium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            CommitmentStatusControls(
                currentStatus = commitment.status,
                onStatusChange = onStatusChange
            )
        }
    }
}
