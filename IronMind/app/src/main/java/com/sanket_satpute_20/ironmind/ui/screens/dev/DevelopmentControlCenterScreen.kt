package com.sanket_satpute_20.ironmind.ui.screens.dev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanket_satpute_20.ironmind.BuildConfig
import com.sanket_satpute_20.ironmind.data.ai.DiagnosticStep

@Composable
fun DevelopmentControlCenterScreen(
    modifier: Modifier = Modifier,
    viewModel: DevelopmentControlCenterViewModel = viewModel(factory = DevelopmentControlCenterViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🛠 Development Control Center",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "WARNING: This is a debug-only facility. It is not part of the normal user experience.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }

        // ── Cloudflare Worker & Firebase Diagnostics ──────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cloudflare Worker & Firebase Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Verifies Firebase initialization, anonymous authentication, fresh ID and App Check token acquisition, and Worker /health connectivity without invoking model inference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { viewModel.runCloudflareWorkerDiagnostic() },
                    enabled = !uiState.isDiagnosticRunning && !uiState.isAuthVerificationRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isDiagnosticRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying Connectivity...")
                    } else {
                        Text("Run Connectivity Diagnostic")
                    }
                }

                uiState.diagnosticReport?.let { report ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = if (report.allPassed) "✅ ALL VERIFICATIONS PASSED" else "⚠️ ISSUES DETECTED",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (report.allPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )

                    DiagnosticStepRow(step = report.firebaseInitStep)
                    DiagnosticStepRow(step = report.anonymousAuthStep)
                    DiagnosticStepRow(step = report.idTokenStep)
                    DiagnosticStepRow(step = report.appCheckStep)
                    DiagnosticStepRow(step = report.workerHealthStep)
                }

                // ── Direct Live Auth Verification Action (Debug Only) ────────
                if (BuildConfig.DEBUG) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Live Token Verification (POST /v1/auth/verify)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Text(
                        text = "Sends current ID token and debug App Check token from memory directly to http://localhost:8787/v1/auth/verify. Bypasses AI inference and D1 completely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { viewModel.verifyWorkerAuth() },
                        enabled = !uiState.isAuthVerificationRunning && !uiState.isDiagnosticRunning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        if (uiState.isAuthVerificationRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifying Tokens with Worker...")
                        } else {
                            Text("Verify Worker Auth (POST /v1/auth/verify)")
                        }
                    }

                    uiState.authVerificationResult?.let { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (result.isSuccess) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (result.isSuccess) "✅ AUTHENTICATION VERIFIED (HTTP 200)" else "❌ VERIFICATION FAILED (HTTP ${result.statusCode})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (result.isSuccess) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.isSuccess) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }
                    }

                    // ── Single Controlled Production Inference Test (Debug Only) ──
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Production Inference Test (POST /v1/process)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Sends exactly one INTENT_EXTRACTION request ('I want to improve my daily consistency.') to the production Cloudflare Worker using in-process Firebase Auth and App Check tokens. Retries are strictly disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { viewModel.testProductionInference() },
                        enabled = !uiState.isProductionInferenceRunning && !uiState.isAuthVerificationRunning && !uiState.isDiagnosticRunning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (uiState.isProductionInferenceRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running Single Inference...")
                        } else {
                            Text("Test Single Production Inference (POST /v1/process)")
                        }
                    }

                    uiState.productionInferenceResult?.let { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (result.isSuccess) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (result.isSuccess) "✅ INFERENCE SUCCEEDED (HTTP ${result.statusCode})" else "❌ INFERENCE FAILED (HTTP ${result.statusCode})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (result.isSuccess) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (result.isSuccess) {
                                    result.intentDescription?.let {
                                        Text(
                                            text = "Intent: $it",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    result.confidence?.let {
                                        Text(
                                            text = "Confidence: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    result.reasoning?.let {
                                        Text(
                                            text = "Reasoning: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                } else {
                                    result.errorMessage?.let {
                                        Text(
                                            text = "Error: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                if (result.remainingMinute != null || result.remainingDaily != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Rate Limits: ${result.remainingMinute ?: "?"}/min remaining, ${result.remainingDaily ?: "?"}/day remaining",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (result.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                if (result.cfRay != null) {
                                    Text(
                                        text = "CF-Ray: ${result.cfRay}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (result.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        StatCard(title = "Events", count = uiState.eventCount)
        StatCard(title = "Observations", count = uiState.observationCount)
        StatCard(title = "Memories", count = uiState.memoryCount)
        StatCard(title = "Patterns", count = uiState.patternCount)
        StatCard(title = "Decisions", count = uiState.decisionCount)
        StatCard(title = "Interventions", count = uiState.interventionCount)
        StatCard(title = "Experiments", count = uiState.experimentCount)
        StatCard(title = "Sync Outbox", count = uiState.outboxCount)
        StatCard(title = "Protection Rules", count = uiState.protectionRuleCount)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DiagnosticStepRow(step: DiagnosticStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (step.isSuccess) "✓" else "✗",
            color = if (step.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Column {
            Text(
                text = step.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = step.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatCard(title: String, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
