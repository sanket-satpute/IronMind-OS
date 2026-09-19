package com.sanket_satpute_20.ironmind.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus

@Composable
fun CommitmentStatusControls(
    currentStatus: CommitmentStatus,
    onStatusChange: (CommitmentStatus, ResultStatus?) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingStatus by remember { mutableStateOf<CommitmentStatus?>(null) }

    if (pendingStatus != null) {
        OutcomeSelectionDialog(
            status = pendingStatus!!,
            onConfirm = { resultStatus -> 
                onStatusChange(pendingStatus!!, resultStatus)
                pendingStatus = null
            },
            onDismiss = { pendingStatus = null }
        )
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        when (currentStatus) {
            CommitmentStatus.PLANNED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.COMMITTED, null) }) {
                    Text("Commit")
                }
            }
            CommitmentStatus.COMMITTED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.STARTED, null) }) {
                    Text("Start")
                }
                Button(
                    onClick = { pendingStatus = CommitmentStatus.POSTPONED },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Postpone")
                }
                Button(
                    onClick = { pendingStatus = CommitmentStatus.MISSED },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Missed")
                }
            }
            CommitmentStatus.STARTED -> {
                Button(onClick = { pendingStatus = CommitmentStatus.COMPLETED }) {
                    Text("Complete")
                }
                Button(
                    onClick = { pendingStatus = CommitmentStatus.POSTPONED },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Postpone")
                }
                Button(
                    onClick = { pendingStatus = CommitmentStatus.MISSED },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Missed")
                }
            }
            CommitmentStatus.POSTPONED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.COMMITTED, null) }) {
                    Text("Re-Commit")
                }
            }
            CommitmentStatus.MISSED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.RECOVERED, null) }) {
                    Text("Recover")
                }
                Button(
                    onClick = { pendingStatus = CommitmentStatus.ABANDONED },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Abandon")
                }
            }
            CommitmentStatus.COMPLETED,
            CommitmentStatus.RECOVERED,
            CommitmentStatus.ABANDONED -> {
                // Terminal states have no forward transitions
            }
        }
    }
}

@Composable
fun OutcomeSelectionDialog(
    status: CommitmentStatus,
    onConfirm: (ResultStatus?) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Outcome") },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("What was the result of this action?")
                Button(onClick = { onConfirm(ResultStatus.POSITIVE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Positive (Success)")
                }
                Button(onClick = { onConfirm(ResultStatus.NEUTRAL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Neutral (As expected/Okay)")
                }
                Button(onClick = { onConfirm(ResultStatus.NEGATIVE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Negative (Failure/Unexpected)")
                }
                Button(onClick = { onConfirm(ResultStatus.PARTIAL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Partial (Incomplete)")
                }
                androidx.compose.material3.TextButton(onClick = { onConfirm(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip Outcome")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
