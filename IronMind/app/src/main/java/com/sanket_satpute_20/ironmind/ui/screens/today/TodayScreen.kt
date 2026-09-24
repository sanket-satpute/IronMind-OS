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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
    onNavigateToProtection: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    TodayScreenContent(
        modifier = modifier,
        uiState = uiState,
        onStatusChange = { id, newStatus, outcome -> viewModel.updateCommitmentStatus(id, newStatus, outcome) },
        onNavigateToProtection = onNavigateToProtection,
        onScheduleClick = { id, time -> viewModel.scheduleCommitment(id, time) },
        onCancelScheduleClick = { id -> viewModel.cancelSchedule(id) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreenContent(
    modifier: Modifier = Modifier,
    uiState: TodayUiState,
    onStatusChange: (String, CommitmentStatus, ResultStatus?) -> Unit,
    onNavigateToProtection: () -> Unit,
    onScheduleClick: (String, Long) -> Unit,
    onCancelScheduleClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = onNavigateToProtection) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Protection"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = modifier.fillMaxSize().padding(paddingValues)) {
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
                        TodayContent(
                            commitments = state.activeCommitments,
                            onStatusChange = onStatusChange,
                            onScheduleClick = onScheduleClick,
                            onCancelScheduleClick = onCancelScheduleClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodayContent(
    commitments: List<Commitment>,
    onStatusChange: (String, CommitmentStatus, ResultStatus?) -> Unit,
    onScheduleClick: (String, Long) -> Unit,
    onCancelScheduleClick: (String) -> Unit
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
                    onStatusChange = { newStatus, outcome -> onStatusChange(nextAction.id, newStatus, outcome) },
                    onScheduleClick = { time -> onScheduleClick(nextAction.id, time) },
                    onCancelScheduleClick = { onCancelScheduleClick(nextAction.id) }
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
                    onStatusChange = { newStatus, outcome -> onStatusChange(commitment.id, newStatus, outcome) },
                    onScheduleClick = { time -> onScheduleClick(commitment.id, time) },
                    onCancelScheduleClick = { onCancelScheduleClick(commitment.id) }
                )
            }
        }
    }
}

@Composable
fun CommitmentCard(
    commitment: Commitment,
    isNextAction: Boolean,
    onStatusChange: (CommitmentStatus, ResultStatus?) -> Unit,
    onScheduleClick: (Long) -> Unit,
    onCancelScheduleClick: () -> Unit
) {
    val context = LocalContext.current
    
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
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (commitment.scheduledStartAt != null) {
                    val formattedTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(commitment.scheduledStartAt))
                    Text(
                        text = "Scheduled: $formattedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancelScheduleClick) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Cancel Schedule", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                IconButton(onClick = {
                    showDateTimePicker(context, commitment.scheduledStartAt) { selectedTime ->
                        onScheduleClick(selectedTime)
                    }
                }) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = "Schedule", tint = MaterialTheme.colorScheme.secondary)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            CommitmentStatusControls(
                currentStatus = commitment.status,
                onStatusChange = onStatusChange
            )
        }
    }
}
