package com.sanket_satpute_20.ironmind.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus

@Composable
fun CommitmentStatusControls(
    currentStatus: CommitmentStatus,
    onStatusChange: (CommitmentStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        when (currentStatus) {
            CommitmentStatus.PLANNED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.COMMITTED) }) {
                    Text("Commit")
                }
            }
            CommitmentStatus.COMMITTED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.STARTED) }) {
                    Text("Start")
                }
                Button(
                    onClick = { onStatusChange(CommitmentStatus.POSTPONED) },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Postpone")
                }
                Button(
                    onClick = { onStatusChange(CommitmentStatus.MISSED) },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Missed")
                }
            }
            CommitmentStatus.STARTED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.COMPLETED) }) {
                    Text("Complete")
                }
                Button(
                    onClick = { onStatusChange(CommitmentStatus.POSTPONED) },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Postpone")
                }
                Button(
                    onClick = { onStatusChange(CommitmentStatus.MISSED) },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Missed")
                }
            }
            CommitmentStatus.POSTPONED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.COMMITTED) }) {
                    Text("Re-Commit")
                }
            }
            CommitmentStatus.MISSED -> {
                Button(onClick = { onStatusChange(CommitmentStatus.RECOVERED) }) {
                    Text("Recover")
                }
                Button(
                    onClick = { onStatusChange(CommitmentStatus.ABANDONED) },
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
