package com.sanket_satpute_20.ironmind.ui.screens.today

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.ui.components.CommitmentStatusControls
import com.sanket_satpute_20.ironmind.ui.components.InterventionSuggestionCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.briefing.DailyBriefing
import com.sanket_satpute_20.ironmind.domain.model.briefing.GoalPlanAttentionItem
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
    onNavigateToProtection: () -> Unit = {},
    onNavigateToReflection: () -> Unit = {},
    onNavigateToPlan: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val showQuickAddDialog by viewModel.showQuickAddDialog.collectAsState()

    TodayScreenContent(
        modifier = modifier,
        uiState = uiState,
        onStatusChange = { id, newStatus, outcome -> viewModel.updateCommitmentStatus(id, newStatus, outcome) },
        onNavigateToProtection = onNavigateToProtection,
        onNavigateToReflection = onNavigateToReflection,
        onNavigateToPlan = onNavigateToPlan,
        onOpenQuickAdd = viewModel::openQuickAdd,
        onScheduleClick = { id, time -> viewModel.scheduleCommitment(id, time) },
        onCancelScheduleClick = { id -> viewModel.cancelSchedule(id) },
        onInterventionResponse = viewModel::handleInterventionResponse,
        onAskIronMind = viewModel::askIronMind,
        onClearUserMessage = viewModel::clearUserMessage,
        onRefreshBriefing = viewModel::refreshDailyBriefing,
        onAcceptAction = viewModel::acceptAction,
        onRejectAction = viewModel::rejectAction,
        onDismissAction = viewModel::dismissAction,
        onDeferAction = viewModel::deferAction,
        onExecuteAction = viewModel::executeAction,
        onRecordActionOutcome = { action, status, feedback -> viewModel.recordActionOutcome(action, status, feedback = feedback) },
        onMarkInsightUseful = viewModel::markInsightUseful,
        onDismissInsight = viewModel::dismissInsight,
        onReviewInsightLater = viewModel::reviewInsightLater
    )

    if (showQuickAddDialog) {
        QuickAddCommitmentBottomSheet(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreenContent(
    modifier: Modifier = Modifier,
    uiState: TodayUiState,
    onStatusChange: (String, CommitmentStatus, ResultStatus?) -> Unit,
    onNavigateToProtection: () -> Unit,
    onNavigateToReflection: () -> Unit,
    onNavigateToPlan: (String) -> Unit,
    onOpenQuickAdd: () -> Unit,
    onScheduleClick: (String, Long) -> Unit,
    onCancelScheduleClick: (String) -> Unit,
    onInterventionResponse: (InterventionRecommendation, HandleInterventionResultUseCase.Action, String?, CorrectionScope) -> Unit,
    onAskIronMind: () -> Unit,
    onClearUserMessage: () -> Unit,
    onRefreshBriefing: () -> Unit = {},
    onAcceptAction: (ProposedAction) -> Unit = {},
    onRejectAction: (ProposedAction, String?) -> Unit = { _, _ -> },
    onDismissAction: (ProposedAction) -> Unit = {},
    onDeferAction: (ProposedAction, Long) -> Unit = { _, _ -> },
    onExecuteAction: (ProposedAction) -> Unit = {},
    onRecordActionOutcome: (ProposedAction, ResultStatus, String?) -> Unit = { _, _, _ -> },
    onMarkInsightUseful: (String) -> Unit = {},
    onDismissInsight: (String) -> Unit = {},
    onReviewInsightLater: (String) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    if (uiState is TodayUiState.Success) {
        val userMessage = uiState.userMessage
        if (userMessage != null) {
            LaunchedEffect(userMessage) {
                snackbarHostState.showSnackbar(userMessage)
                onClearUserMessage()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState is TodayUiState.Success) {
                ExtendedFloatingActionButton(
                    onClick = { if (!uiState.isGeneratingRecommendation) onAskIronMind() },
                    icon = {
                        if (uiState.isGeneratingRecommendation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Info, contentDescription = "Ask IronMind")
                        }
                    },
                    text = { Text(if (uiState.isGeneratingRecommendation) "Thinking..." else "Ask IronMind") }
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = onOpenQuickAdd) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Quick Add Commitment"
                        )
                    }
                    IconButton(onClick = onNavigateToReflection) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Night Reflection"
                        )
                    }
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
                            pendingRecommendations = state.pendingRecommendations,
                            dueTodayTasks = state.dueTodayTasks,
                            overdueTasks = state.overdueTasks,
                            dailyBriefing = state.dailyBriefing,
                            proposedActions = state.proposedActions,
                            isBriefingLoading = state.isBriefingLoading,
                            onStatusChange = onStatusChange,
                            onScheduleClick = onScheduleClick,
                            onCancelScheduleClick = onCancelScheduleClick,
                            onInterventionResponse = onInterventionResponse,
                            onNavigateToPlan = onNavigateToPlan,
                            onOpenQuickAdd = onOpenQuickAdd,
                            onRefreshBriefing = onRefreshBriefing,
                            onAcceptAction = onAcceptAction,
                            onRejectAction = onRejectAction,
                            onDismissAction = onDismissAction,
                            onDeferAction = onDeferAction,
                            onExecuteAction = onExecuteAction,
                            onRecordActionOutcome = onRecordActionOutcome,
                            onMarkInsightUseful = onMarkInsightUseful,
                            onDismissInsight = onDismissInsight,
                            onReviewInsightLater = onReviewInsightLater
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
    pendingRecommendations: List<InterventionRecommendation>,
    dueTodayTasks: List<DueTaskItem>,
    overdueTasks: List<DueTaskItem>,
    dailyBriefing: DailyBriefing? = null,
    proposedActions: List<ProposedAction> = emptyList(),
    isBriefingLoading: Boolean = false,
    onStatusChange: (String, CommitmentStatus, ResultStatus?) -> Unit,
    onScheduleClick: (String, Long) -> Unit,
    onCancelScheduleClick: (String) -> Unit,
    onInterventionResponse: (InterventionRecommendation, HandleInterventionResultUseCase.Action, String?, CorrectionScope) -> Unit,
    onNavigateToPlan: (String) -> Unit,
    onOpenQuickAdd: () -> Unit,
    onRefreshBriefing: () -> Unit = {},
    onAcceptAction: (ProposedAction) -> Unit = {},
    onRejectAction: (ProposedAction, String?) -> Unit = { _, _ -> },
    onDismissAction: (ProposedAction) -> Unit = {},
    onDeferAction: (ProposedAction, Long) -> Unit = { _, _ -> },
    onExecuteAction: (ProposedAction) -> Unit = {},
    onRecordActionOutcome: (ProposedAction, ResultStatus, String?) -> Unit = { _, _, _ -> },
    onMarkInsightUseful: (String) -> Unit = {},
    onDismissInsight: (String) -> Unit = {},
    onReviewInsightLater: (String) -> Unit = {}
) {
    val hasAnyContent = commitments.isNotEmpty() ||
            pendingRecommendations.isNotEmpty() ||
            dueTodayTasks.isNotEmpty() ||
            overdueTasks.isNotEmpty() ||
            dailyBriefing != null ||
            proposedActions.isNotEmpty()

    if (!hasAnyContent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "No active commitments or due tasks for today.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenQuickAdd) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Commitment")
                }
            }
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
        // 1. Daily Intelligence Briefing Header
        if (dailyBriefing != null || isBriefingLoading) {
            item {
                DailyBriefingCard(
                    briefing = dailyBriefing,
                    isLoading = isBriefingLoading,
                    onRefresh = onRefreshBriefing,
                    onNavigateToPlan = onNavigateToPlan
                )
            }
        }

        // 2. Overdue Tasks Section
        if (overdueTasks.isNotEmpty()) {
            item {
                Text(
                    text = "Overdue Tasks (${overdueTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(overdueTasks, key = { "overdue_${it.task.id}" }) { item ->
                DueTaskCard(item = item, onNavigateToPlan = onNavigateToPlan)
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // 3. Recommended Next Actions
        val activeActions = proposedActions.filter {
            it.reviewStatus == ActionReviewStatus.PROPOSED ||
                    it.reviewStatus == ActionReviewStatus.ACCEPTED ||
                    it.reviewStatus == ActionReviewStatus.EXECUTING ||
                    it.reviewStatus == ActionReviewStatus.EXECUTED ||
                    it.reviewStatus == ActionReviewStatus.EXECUTION_FAILED
        }.take(3)
        if (activeActions.isNotEmpty()) {
            item {
                Text(
                    text = "Recommended Next Actions (${activeActions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(activeActions, key = { it.id }) { action ->
                ProposedActionCard(
                    action = action,
                    onAccept = { onAcceptAction(action) },
                    onReject = { reason -> onRejectAction(action, reason) },
                    onDismiss = { onDismissAction(action) },
                    onDefer = { deferMs -> onDeferAction(action, deferMs) },
                    onExecute = { onExecuteAction(action) },
                    onRecordOutcome = { status -> onRecordActionOutcome(action, status, null) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // 4. Pending Recommendations
        if (pendingRecommendations.isNotEmpty()) {
            items(pendingRecommendations, key = { it.id }) { recommendation ->
                InterventionSuggestionCard(
                    recommendation = recommendation,
                    onAction = { action, text, scope -> onInterventionResponse(recommendation, action, text, scope) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 5. Commitments: Next Action
        if (commitments.isNotEmpty()) {
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
        }

        // 6. Due Today Tasks Section
        if (dueTodayTasks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Due Today (${dueTodayTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(dueTodayTasks, key = { "due_${it.task.id}" }) { item ->
                DueTaskCard(item = item, onNavigateToPlan = onNavigateToPlan)
            }
        }

        // 7. Commitments: Later Today
        if (commitments.isNotEmpty()) {
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

        // 8. Relevant Personal Insights
        val topInsights = dailyBriefing?.relevantInsights?.take(3) ?: emptyList()
        if (topInsights.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Personal Insights (${topInsights.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(topInsights, key = { "briefing_ins_${it.id}" }) { insight ->
                BriefingInsightCard(
                    insight = insight,
                    onMarkUseful = { onMarkInsightUseful(insight.id) },
                    onDismiss = { onDismissInsight(insight.id) },
                    onReviewLater = { onReviewInsightLater(insight.id) }
                )
            }
        }
    }
}

@Composable
fun DueTaskCard(
    item: DueTaskItem,
    onNavigateToPlan: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (item.task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeColor = if (item.isOverdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                    Badge(containerColor = badgeColor) {
                        Text(
                            text = if (item.isOverdue) "Overdue" else "Due Today",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    if (item.planTitle != null) {
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = item.planTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (item.task.planId != null) {
                    TextButton(onClick = { onNavigateToPlan(item.task.planId) }) {
                        Text("View in Plan", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddCommitmentBottomSheet(viewModel: TodayViewModel) {
    val title by viewModel.quickAddTitle.collectAsState()
    val description by viewModel.quickAddDescription.collectAsState()
    val priority by viewModel.quickAddPriority.collectAsState()
    val isQuickAdding by viewModel.isQuickAdding.collectAsState()
    val quickAddError by viewModel.quickAddError.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = viewModel::closeQuickAdd,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Quick Add Commitment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Commit to an action directly for today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = viewModel::updateQuickAddTitle,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = viewModel::updateQuickAddDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Priority:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = priority == 1,
                    onClick = { viewModel.updateQuickAddPriority(1) },
                    label = { Text("Low") }
                )
                FilterChip(
                    selected = priority == 2,
                    onClick = { viewModel.updateQuickAddPriority(2) },
                    label = { Text("Medium") }
                )
                FilterChip(
                    selected = priority == 3,
                    onClick = { viewModel.updateQuickAddPriority(3) },
                    label = { Text("High") }
                )
            }

            if (quickAddError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = quickAddError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::saveQuickAddCommitment,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isQuickAdding
            ) {
                if (isQuickAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Commit for Today")
                }
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

@Composable
fun DailyBriefingCard(
    briefing: DailyBriefing?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigateToPlan: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Briefing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Briefing")
                    }
                }
            }

            if (briefing != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = briefing.attentionSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (briefing.dataAvailabilityNote != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = briefing.dataAvailabilityNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (briefing.goalsAndPlansNeedingAttention.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Attention Needed:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    for (attn in briefing.goalsAndPlansNeedingAttention) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${attn.entityType}: ${attn.title} - ${attn.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (attn.entityType == "PLAN") {
                                TextButton(onClick = { onNavigateToPlan(attn.entityId) }) {
                                    Text("View", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                if (briefing.discoveredPatterns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Observed Behaviour Patterns:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    for (pattern in briefing.discoveredPatterns.take(2)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${pattern.patternType}: ${pattern.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                val confStr = if (pattern.confidence > 0f) "${(pattern.confidence * 100).toInt()}% conf • " else ""
                                val epistemicLabel = if (pattern.epistemicAuthority == EpistemicAuthority.AI_HYPOTHESIS) {
                                    "Unconfirmed hypothesis • Correlation is not causation"
                                } else {
                                    pattern.epistemicAuthority.name
                                }
                                Text(
                                    text = "$confStr${pattern.occurrences} observations • $epistemicLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                if (briefing.evidenceBackedBarriers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Active Obstacle Hypotheses:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    for (barrier in briefing.evidenceBackedBarriers.take(2)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${barrier.category.name}: ${barrier.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                val statusLabel = if (barrier.isUserConfirmed) "Confirmed by you" else "Unconfirmed Hypothesis"
                                Text(
                                    text = "$statusLabel • ${barrier.supportingEvidenceCount} evidence items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProposedActionCard(
    action: ProposedAction,
    onAccept: () -> Unit,
    onReject: (String?) -> Unit,
    onDismiss: () -> Unit,
    onDefer: (Long) -> Unit,
    onExecute: () -> Unit,
    onRecordOutcome: (ResultStatus) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (action.reviewStatus) {
                ActionReviewStatus.ACCEPTED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ActionReviewStatus.EXECUTED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = action.category.name.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = action.epistemicAuthority.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = action.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (action.suggestedSubTasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Suggested steps:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                for (step in action.suggestedSubTasks) {
                    Text(
                        text = "• $step",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Lifecycle Controls
            when (action.reviewStatus) {
                ActionReviewStatus.PROPOSED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = { onDefer(System.currentTimeMillis() + 86400000L) }) {
                            Text("Defer")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = { onReject(null) }) {
                            Text("Reject")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(onClick = onAccept) {
                            Text("Accept")
                        }
                    }
                }
                ActionReviewStatus.ACCEPTED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Approved by you (pending execution)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row {
                            TextButton(onClick = { onReject(null) }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = onExecute) {
                                Text("Execute Action")
                            }
                        }
                    }
                }
                ActionReviewStatus.EXECUTING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Executing action...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                ActionReviewStatus.EXECUTION_FAILED -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Execution failed: ${action.executionResult?.message ?: "An error occurred"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
                ActionReviewStatus.EXECUTED -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Action executed: ${action.executionResult?.message ?: "Completed"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Was this helpful?",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onRecordOutcome(ResultStatus.NEGATIVE) }) {
                                Text("No")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { onRecordOutcome(ResultStatus.POSITIVE) }) {
                                Text("Yes, helped")
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Status: ${action.reviewStatus.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun BriefingInsightCard(
    insight: PersonalInsight,
    onMarkUseful: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onReviewLater: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = insight.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = if (insight.epistemicAuthority == EpistemicAuthority.AI_HYPOTHESIS) "AI Hypothesis" else insight.epistemicAuthority.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = insight.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onReviewLater) {
                    Text("Later", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = onMarkUseful,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Useful", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
