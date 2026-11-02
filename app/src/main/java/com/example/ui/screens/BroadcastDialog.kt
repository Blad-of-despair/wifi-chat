package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BroadcastDialog(
    onSendBroadcast: (title: String, message: String, urgency: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("Dinner is ready! 🍲") }
    var message by remember { mutableStateOf("Come downstairs everyone, the food is hot on the table.") }
    var urgency by remember { mutableStateOf("HIGH") }

    val quickPresets = listOf(
        "Dinner is ready! 🍲",
        "Help downstairs please 📦",
        "Leaving in 10 minutes 🚗",
        "Quiet time / sleeping 🤫"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "📢 Broadcast to Whole Family",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Broadcasts send an instant announcement with high priority alert to every phone in the house.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPresets.take(2).forEach { preset ->
                        FilterChip(
                            selected = title == preset,
                            onClick = { title = preset },
                            label = { Text(preset.take(16) + "...", style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Announcement Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Details / Message") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = urgency == "NORMAL",
                        onClick = { urgency = "NORMAL" },
                        label = { Text("Normal Alert") }
                    )
                    FilterChip(
                        selected = urgency == "HIGH",
                        onClick = { urgency = "HIGH" },
                        label = { Text("Loud / High Alert 🔔") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSendBroadcast(title.trim(), message.trim(), urgency)
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Broadcast Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
