package com.sanket_satpute_20.ironmind.ui.screens.reflection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightReflectionScreen(
    viewModel: NightReflectionViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var reflectionText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Night Reflection") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is NightReflectionUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is NightReflectionUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is NightReflectionUiState.Success -> {
                    if (state.isSaved) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                "Reflection Saved!",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onNavigateBack) {
                                Text("Back to Dashboard")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                "Today's Summary",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Total Commitments: ${state.summary.totalPlanned}")
                                    Text("Completed: ${state.summary.completed}")
                                    Text("Missed: ${state.summary.missed}")
                                    Text("Postponed: ${state.summary.postponed}")
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                "Reflection",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = reflectionText,
                                onValueChange = { reflectionText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                placeholder = { Text("How did today go? What did you learn?") },
                                maxLines = 10
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.saveReflection(content = reflectionText) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = reflectionText.isNotBlank()
                            ) {
                                Text("Save Reflection")
                            }
                        }
                    }
                }
            }
        }
    }
}
