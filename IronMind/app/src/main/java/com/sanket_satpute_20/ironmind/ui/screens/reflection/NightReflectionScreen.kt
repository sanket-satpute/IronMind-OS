package com.sanket_satpute_20.ironmind.ui.screens.reflection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightReflectionScreen(
    viewModel: NightReflectionViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val reflectionText by viewModel.reflectionText.collectAsState()

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
                    if (state.isSaved && state.existingReflection != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Today's Reflection",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.existingReflection,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onNavigateBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text("Back to Dashboard")
                            }
                        }
                    } else if (state.isSaved) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(16.dp)
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            item {
                                Text(
                                    "Result Review",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (state.summary.completed.isEmpty()) {
                                    Text("No commitments completed today.")
                                }
                            }
                            
                            items(state.summary.completed) { commitment ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(commitment.title, modifier = Modifier.padding(16.dp))
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    "Missed / Postponed Review",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (state.summary.missed.isEmpty() && state.summary.postponed.isEmpty()) {
                                    Text("Nothing missed or postponed today!")
                                }
                            }
                            
                            items(state.summary.missed) { commitment ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Text("${commitment.title} (Missed)", modifier = Modifier.padding(16.dp))
                                }
                            }
                            
                            items(state.summary.postponed) { commitment ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text("${commitment.title} (Postponed)", modifier = Modifier.padding(16.dp))
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    "Reflection",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedTextField(
                                    value = reflectionText,
                                    onValueChange = { viewModel.updateReflectionText(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
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
}
