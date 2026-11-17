package com.sanket_satpute_20.ironmind.ui.screens.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.ui.components.ProgressBarWithStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: String,
    modifier: Modifier = Modifier,
    viewModel: GoalDetailViewModel = viewModel(factory = GoalDetailViewModel.Factory),
    onNavigateBack: () -> Unit = {},
    onNavigateToPlan: (String) -> Unit = {}
) {
    LaunchedEffect(goalId) {
        viewModel.loadData(goalId)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, goalId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadData(goalId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val showCreateForm by viewModel.showCreateForm.collectAsState()
    val showEditForm by viewModel.showEditForm.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Goal Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is GoalDetailUiState.Success) {
                        IconButton(onClick = { viewModel.openEditForm() }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Goal")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Only show FAB if we successfully loaded the goal
            if (uiState is GoalDetailUiState.Success) {
                FloatingActionButton(onClick = { viewModel.openCreateForm() }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Plan")
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
                is GoalDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is GoalDetailUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is GoalDetailUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Goal Info Header
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = state.goal.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.openEditForm() }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Goal",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statusColor = when (state.goal.status) {
                                        GoalStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                        GoalStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                                        GoalStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                                        GoalStatus.ARCHIVED, GoalStatus.ENDED -> MaterialTheme.colorScheme.outline
                                    }
                                    Badge(containerColor = statusColor) {
                                        Text(
                                            text = state.goal.status.name,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(
                                            text = "Importance: ${state.goal.importance}/10",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.goal.description.ifBlank { "No description" },
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (state.goal.why.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Why: ${state.goal.why}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                ProgressBarWithStats(progress = state.goalProgress)

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
                                    FilterChip(
                                        selected = state.goal.status == GoalStatus.ACTIVE,
                                        onClick = { viewModel.updateStatus(GoalStatus.ACTIVE) },
                                        label = { Text("Active") }
                                    )
                                    FilterChip(
                                        selected = state.goal.status == GoalStatus.PAUSED,
                                        onClick = { viewModel.updateStatus(GoalStatus.PAUSED) },
                                        label = { Text("Pause") }
                                    )
                                    FilterChip(
                                        selected = state.goal.status == GoalStatus.COMPLETED,
                                        onClick = { viewModel.updateStatus(GoalStatus.COMPLETED) },
                                        label = { Text("Complete") }
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        // Plans List
                        Text(
                            text = "Plans",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )

                        if (state.plans.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No plans yet. Create one!",
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
                                items(state.plans, key = { it.id }) { plan ->
                                    PlanCard(
                                        plan = plan,
                                        progress = state.planProgressMap[plan.id] ?: ProgressInfo.ZERO,
                                        onClick = { onNavigateToPlan(plan.id) }
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
        CreatePlanBottomSheet(viewModel = viewModel)
    }

    if (showEditForm) {
        EditGoalBottomSheet(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanCard(
    plan: Plan,
    progress: ProgressInfo = ProgressInfo.ZERO,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = plan.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (plan.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ProgressBarWithStats(progress = progress)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanBottomSheet(viewModel: GoalDetailViewModel) {
    val newPlanTitle by viewModel.newPlanTitle.collectAsState()
    val newPlanDescription by viewModel.newPlanDescription.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

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
                text = "Create Plan",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = newPlanTitle,
                onValueChange = viewModel::updateNewPlanTitle,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newPlanDescription,
                onValueChange = viewModel::updateNewPlanDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

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
                onClick = viewModel::createPlan,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Plan")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGoalBottomSheet(viewModel: GoalDetailViewModel) {
    val editTitle by viewModel.editGoalTitle.collectAsState()
    val editDescription by viewModel.editGoalDescription.collectAsState()
    val editWhy by viewModel.editGoalWhy.collectAsState()
    val editImportance by viewModel.editGoalImportance.collectAsState()
    val editStatus by viewModel.editGoalStatus.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val editError by viewModel.editError.collectAsState()

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
                text = "Edit Goal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = editTitle,
                onValueChange = viewModel::updateEditGoalTitle,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editDescription,
                onValueChange = viewModel::updateEditGoalDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editWhy,
                onValueChange = viewModel::updateEditGoalWhy,
                label = { Text("Why is this important?") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))
            val maxImportance = if (editImportance > 5f) 10f else 5f
            val steps = if (maxImportance == 10f) 8 else 3
            Text("Importance: ${editImportance.toInt()}/${maxImportance.toInt()}")
            Slider(
                value = editImportance,
                onValueChange = viewModel::updateEditGoalImportance,
                valueRange = 1f..maxImportance,
                steps = steps
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Status:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = editStatus == GoalStatus.ACTIVE,
                    onClick = { viewModel.updateEditGoalStatus(GoalStatus.ACTIVE) },
                    label = { Text("Active") }
                )
                FilterChip(
                    selected = editStatus == GoalStatus.PAUSED,
                    onClick = { viewModel.updateEditGoalStatus(GoalStatus.PAUSED) },
                    label = { Text("Paused") }
                )
                FilterChip(
                    selected = editStatus == GoalStatus.COMPLETED,
                    onClick = { viewModel.updateEditGoalStatus(GoalStatus.COMPLETED) },
                    label = { Text("Completed") }
                )
            }

            if (editError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = editError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::saveGoalEdit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isEditing
            ) {
                if (isEditing) {
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
