package com.sanket_satpute_20.ironmind.ui.screens.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.domain.model.Goal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val showCreateForm by viewModel.showCreateForm.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Goals") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openCreateForm() }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Goal")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is GoalsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is GoalsUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is GoalsUiState.Success -> {
                    if (state.goals.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No active goals yet.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "What are you trying to accomplish?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.goals, key = { it.id }) { goal ->
                                GoalCard(goal = goal)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateForm) {
        CreateGoalBottomSheet(viewModel = viewModel)
    }
}

@Composable
fun GoalCard(goal: Goal) {
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
                text = goal.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (goal.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = goal.description,
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
                    Text("Importance: ${goal.importance}/10")
                }
                Text(
                    text = goal.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalBottomSheet(viewModel: GoalsViewModel) {
    val newGoalTitle by viewModel.newGoalTitle.collectAsState()
    val newGoalDescription by viewModel.newGoalDescription.collectAsState()
    val newGoalWhy by viewModel.newGoalWhy.collectAsState()
    val newGoalImportance by viewModel.newGoalImportance.collectAsState()
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
                text = "Create Goal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = newGoalTitle,
                onValueChange = viewModel::updateNewGoalTitle,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newGoalDescription,
                onValueChange = viewModel::updateNewGoalDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newGoalWhy,
                onValueChange = viewModel::updateNewGoalWhy,
                label = { Text("Why is this important?") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Importance: ${newGoalImportance.toInt()}/10")
            Slider(
                value = newGoalImportance,
                onValueChange = viewModel::updateNewGoalImportance,
                valueRange = 1f..10f,
                steps = 8
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
                onClick = viewModel::createGoal,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Goal")
                }
            }
        }
    }
}
