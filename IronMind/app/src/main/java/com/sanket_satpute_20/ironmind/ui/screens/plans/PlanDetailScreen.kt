package com.sanket_satpute_20.ironmind.ui.screens.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.ui.components.ProgressBarWithStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    planId: String,
    modifier: Modifier = Modifier,
    viewModel: PlanDetailViewModel = viewModel(factory = PlanDetailViewModel.Factory),
    onNavigateBack: () -> Unit = {}
) {
    LaunchedEffect(planId) {
        viewModel.loadData(planId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val showCreateForm by viewModel.showCreateForm.collectAsState()
    val editingTask by viewModel.editingTask.collectAsState()
    val isPerformingTaskAction by viewModel.isPerformingTaskAction.collectAsState()
    val taskActionError by viewModel.taskActionError.collectAsState()
    val taskPendingDeletion by viewModel.taskPendingDeletion.collectAsState()
    val deletionSuccessMessage by viewModel.deletionSuccessMessage.collectAsState()
    val showEditPlanForm by viewModel.showEditPlanForm.collectAsState()
    val isPerformingPlanAction by viewModel.isPerformingPlanAction.collectAsState()
    val planActionError by viewModel.planActionError.collectAsState()
    val planUserMessage by viewModel.planUserMessage.collectAsState()
    val showCompletePlanConfirmation by viewModel.showCompletePlanConfirmation.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(deletionSuccessMessage) {
        deletionSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearDeletionSuccessMessage()
        }
    }

    LaunchedEffect(planUserMessage) {
        planUserMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearPlanUserMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Plan Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            // Only show FAB if we successfully loaded the plan
            if (uiState is PlanDetailUiState.Success) {
                FloatingActionButton(onClick = { viewModel.openCreateForm() }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Task")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is PlanDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PlanDetailUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PlanDetailUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Plan Info Header
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = state.plan.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = viewModel::openEditPlanForm) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Plan",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                val statusColor = when (state.plan.status) {
                                    PlanStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                    PlanStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                                    PlanStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                                    PlanStatus.DRAFT, PlanStatus.ABANDONED -> MaterialTheme.colorScheme.outline
                                }
                                Badge(containerColor = statusColor) {
                                    Text(
                                        text = state.plan.status.name,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.plan.description.ifBlank { "No description" },
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                ProgressBarWithStats(progress = state.progress)

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Status:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    when (state.plan.status) {
                                        PlanStatus.ACTIVE -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = { viewModel.updatePlanStatus(PlanStatus.PAUSED) },
                                                label = { Text("Pause") },
                                                enabled = !isPerformingPlanAction
                                            )
                                            FilterChip(
                                                selected = false,
                                                onClick = { viewModel.requestCompletePlan() },
                                                label = { Text("Complete") },
                                                enabled = !isPerformingPlanAction
                                            )
                                        }
                                        PlanStatus.PAUSED -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = { viewModel.updatePlanStatus(PlanStatus.ACTIVE) },
                                                label = { Text("Resume") },
                                                enabled = !isPerformingPlanAction
                                            )
                                            FilterChip(
                                                selected = false,
                                                onClick = { viewModel.requestCompletePlan() },
                                                label = { Text("Complete") },
                                                enabled = !isPerformingPlanAction
                                            )
                                        }
                                        PlanStatus.COMPLETED -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = { viewModel.updatePlanStatus(PlanStatus.ACTIVE) },
                                                label = { Text("Reactivate") },
                                                enabled = !isPerformingPlanAction
                                            )
                                        }
                                        PlanStatus.DRAFT -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = { viewModel.updatePlanStatus(PlanStatus.ACTIVE) },
                                                label = { Text("Activate") },
                                                enabled = !isPerformingPlanAction
                                            )
                                        }
                                        PlanStatus.ABANDONED -> {
                                            // Abandoned plans cannot transition
                                        }
                                    }
                                }
                            }
                        }

                        // Plan Action Error Banner
                        if (planActionError != null && !showEditPlanForm) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = planActionError!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = viewModel::clearPlanActionError) {
                                        Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }

                        // Task Action Error Banner
                        if (taskActionError != null && editingTask == null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = taskActionError!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = viewModel::clearTaskActionError) {
                                        Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // Tasks List
                        Text(
                            text = "Tasks",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )

                        if (state.tasks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No tasks yet. Create one!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.tasks, key = { it.id }) { task ->
                                    val isCommitted = state.committedTaskIds.contains(task.id)
                                    TaskCard(
                                        task = task,
                                        isCommitted = isCommitted,
                                        onCommitClick = { viewModel.commitTaskForToday(task) },
                                        onEditClick = { viewModel.openEditForm(task) },
                                        onDeleteClick = { viewModel.requestDeleteTask(task) },
                                        onStatusChange = { newStatus -> viewModel.updateTaskStatus(task, newStatus) },
                                        isActionInProgress = isPerformingTaskAction
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateForm) {
        CreateTaskBottomSheet(viewModel = viewModel)
    }

    if (editingTask != null) {
        EditTaskBottomSheet(viewModel = viewModel)
    }

    if (taskPendingDeletion != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteTask,
            title = { Text("Delete Task") },
            text = { Text("Are you sure you want to delete \"${taskPendingDeletion!!.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteTask,
                    enabled = !isPerformingTaskAction
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelDeleteTask,
                    enabled = !isPerformingTaskAction
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditPlanForm) {
        EditPlanBottomSheet(viewModel = viewModel)
    }

    if (showCompletePlanConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelCompletePlan,
            title = { Text("Complete Plan") },
            text = { Text("Are you sure you want to mark this plan as completed?") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmCompletePlan,
                    enabled = !isPerformingPlanAction
                ) {
                    Text("Complete", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelCompletePlan,
                    enabled = !isPerformingPlanAction
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TaskCard(
    task: Task,
    isCommitted: Boolean,
    onCommitClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    isActionInProgress: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEditClick,
                        enabled = !isActionInProgress,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Task",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = !isActionInProgress,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Task",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge {
                        Text("Priority: ${task.priority}")
                    }
                    if (task.dueAt != null) {
                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                text = "Due: ${formatEpochDate(task.dueAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                val statusColor = when (task.status) {
                    TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                    TaskStatus.POSTPONED -> MaterialTheme.colorScheme.secondary
                    TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                    TaskStatus.CANCELLED -> MaterialTheme.colorScheme.outline
                    TaskStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(task.status.name, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = statusColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status transition actions & Commit for Today
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (task.status) {
                        TaskStatus.PENDING -> {
                            FilledTonalButton(
                                onClick = { onStatusChange(TaskStatus.IN_PROGRESS) },
                                enabled = !isActionInProgress,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Start", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onStatusChange(TaskStatus.COMPLETED) },
                                enabled = !isActionInProgress,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Complete", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TaskStatus.IN_PROGRESS -> {
                            FilledTonalButton(
                                onClick = { onStatusChange(TaskStatus.COMPLETED) },
                                enabled = !isActionInProgress,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Complete", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onStatusChange(TaskStatus.POSTPONED) },
                                enabled = !isActionInProgress,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Postpone", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TaskStatus.POSTPONED -> {
                            FilledTonalButton(
                                onClick = { onStatusChange(TaskStatus.IN_PROGRESS) },
                                enabled = !isActionInProgress,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Resume", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onStatusChange(TaskStatus.COMPLETED) },
                                enabled = !isActionInProgress,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Complete", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TaskStatus.FAILED -> {
                            FilledTonalButton(
                                onClick = { onStatusChange(TaskStatus.IN_PROGRESS) },
                                enabled = !isActionInProgress,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Retry", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TaskStatus.COMPLETED -> {
                            Text(
                                text = "✓ Finished",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TaskStatus.CANCELLED -> {
                            Text(
                                text = "Cancelled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Commit for Today
                if (task.status != TaskStatus.COMPLETED && task.status != TaskStatus.CANCELLED) {
                    if (isCommitted) {
                        Text(
                            text = "✓ Committed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        OutlinedButton(
                            onClick = onCommitClick,
                            enabled = !isActionInProgress,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Commit Today", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskBottomSheet(viewModel: PlanDetailViewModel) {
    val editTaskTitle by viewModel.editTaskTitle.collectAsState()
    val editTaskDescription by viewModel.editTaskDescription.collectAsState()
    val editTaskPriority by viewModel.editTaskPriority.collectAsState()
    val editTaskDueAt by viewModel.editTaskDueAt.collectAsState()
    val isPerformingTaskAction by viewModel.isPerformingTaskAction.collectAsState()
    val taskActionError by viewModel.taskActionError.collectAsState()
    val editingTask by viewModel.editingTask.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeEditForm() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Edit Task",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = editTaskTitle,
                onValueChange = viewModel::updateEditTaskTitle,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editTaskDescription,
                onValueChange = viewModel::updateEditTaskDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Priority: ${editTaskPriority.toInt()} (1 = Highest, 5 = Lowest)")
            Slider(
                value = editTaskPriority,
                onValueChange = viewModel::updateEditTaskPriority,
                valueRange = 1f..5f,
                steps = 3
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Due Date:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editTaskDueAt != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = { showDatePicker = true },
                            label = { Text("Due: ${formatEpochDate(editTaskDueAt!!)}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Change Due Date",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        IconButton(
                            onClick = { viewModel.updateEditTaskDueAt(null) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Due Date",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Set Due Date",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set Due Date")
                    }
                }
            }

            if (showDatePicker) {
                TaskDatePickerDialog(
                    initialDateMillis = editTaskDueAt,
                    onDateSelected = { selectedDate ->
                        viewModel.updateEditTaskDueAt(selectedDate)
                    },
                    onDismiss = { showDatePicker = false }
                )
            }

            if (taskActionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = taskActionError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::saveEditedTask,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPerformingTaskAction
            ) {
                if (isPerformingTaskAction) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { editingTask?.let { viewModel.requestDeleteTask(it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPerformingTaskAction,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Task",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Task")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskBottomSheet(viewModel: PlanDetailViewModel) {
    val newTaskTitle by viewModel.newTaskTitle.collectAsState()
    val newTaskDescription by viewModel.newTaskDescription.collectAsState()
    val newTaskPriority by viewModel.newTaskPriority.collectAsState()
    val newTaskDueAt by viewModel.newTaskDueAt.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeCreateForm() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Create Task",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = newTaskTitle,
                onValueChange = viewModel::updateNewTaskTitle,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newTaskDescription,
                onValueChange = viewModel::updateNewTaskDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Priority: ${newTaskPriority.toInt()} (1 = Highest, 5 = Lowest)")
            Slider(
                value = newTaskPriority,
                onValueChange = viewModel::updateNewTaskPriority,
                valueRange = 1f..5f,
                steps = 3
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Due Date:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (newTaskDueAt != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = { showDatePicker = true },
                            label = { Text("Due: ${formatEpochDate(newTaskDueAt!!)}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Change Due Date",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        IconButton(
                            onClick = { viewModel.updateNewTaskDueAt(null) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Due Date",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Set Due Date",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set Due Date (Optional)")
                    }
                }
            }

            if (showDatePicker) {
                TaskDatePickerDialog(
                    initialDateMillis = newTaskDueAt,
                    onDateSelected = { selectedDate ->
                        viewModel.updateNewTaskDueAt(selectedDate)
                    },
                    onDismiss = { showDatePicker = false }
                )
            }

            if (saveError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = saveError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::createTask,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Task")
                }
            }
        }
    }
}

private fun formatEpochDate(epochMillis: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(epochMillis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDatePickerDialog(
    initialDateMillis: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: System.currentTimeMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { utcMillis ->
                    val localDate = java.time.Instant.ofEpochMilli(utcMillis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                    val localDueAt = localDate.atTime(23, 59, 59)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    onDateSelected(localDueAt)
                }
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanBottomSheet(viewModel: PlanDetailViewModel) {
    val editPlanTitle by viewModel.editPlanTitle.collectAsState()
    val editPlanDescription by viewModel.editPlanDescription.collectAsState()
    val editPlanStatus by viewModel.editPlanStatus.collectAsState()
    val isPerformingPlanAction by viewModel.isPerformingPlanAction.collectAsState()
    val planActionError by viewModel.planActionError.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeEditPlanForm() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Edit Plan",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = editPlanTitle,
                onValueChange = viewModel::updateEditPlanTitle,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editPlanDescription,
                onValueChange = viewModel::updateEditPlanDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Status:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = editPlanStatus == PlanStatus.ACTIVE,
                    onClick = { viewModel.updateEditPlanStatus(PlanStatus.ACTIVE) },
                    label = { Text("Active") }
                )
                FilterChip(
                    selected = editPlanStatus == PlanStatus.PAUSED,
                    onClick = { viewModel.updateEditPlanStatus(PlanStatus.PAUSED) },
                    label = { Text("Paused") }
                )
                FilterChip(
                    selected = editPlanStatus == PlanStatus.COMPLETED,
                    onClick = { viewModel.updateEditPlanStatus(PlanStatus.COMPLETED) },
                    label = { Text("Completed") }
                )
            }

            if (planActionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = planActionError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::saveEditedPlan,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPerformingPlanAction
            ) {
                if (isPerformingPlanAction) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}
