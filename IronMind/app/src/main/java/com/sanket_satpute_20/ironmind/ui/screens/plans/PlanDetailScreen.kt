package com.sanket_satpute_20.ironmind.ui.screens.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.model.Task

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

    Scaffold(
        modifier = modifier,
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
                                Text(
                                    text = state.plan.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.plan.description.ifBlank { "No description" },
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
                                        onCommitClick = { viewModel.commitTaskForToday(task) }
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
}

@Composable
fun TaskCard(
    task: Task,
    isCommitted: Boolean,
    onCommitClick: () -> Unit
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
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
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
                Badge {
                    Text("Priority: ${task.priority}")
                }
                Text(
                    text = task.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isCommitted) {
                    Text(
                        text = "✓ Committed for Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    OutlinedButton(
                        onClick = onCommitClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Commit for Today", style = MaterialTheme.typography.labelMedium)
                    }
                }
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
