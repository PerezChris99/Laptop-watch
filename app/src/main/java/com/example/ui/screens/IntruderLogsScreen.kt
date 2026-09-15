package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.IntruderLogEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlueSoft
import com.example.ui.theme.MutedSlate500
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WhitePure
import com.example.ui.theme.WhiteSmoke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IntruderLogsScreen(
    logs: List<IntruderLogEntity>,
    onDeleteLog: (Long) -> Unit,
    onClearAllLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showClearDialog by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "SECURITY" -> logs.filter { it.category == "SECURITY" || it.severity == "ALERT" }
            "ACTIONS" -> logs.filter { it.category == "USER_ACTION" }
            "SYSTEM" -> logs.filter { it.category == "SYSTEM" }
            else -> logs
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WhiteSmoke)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top row: Header & Clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Activity & Security Logs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepSlate800
                )
                Text(
                    text = "${logs.size} total activities & events recorded",
                    fontSize = 12.sp,
                    color = MutedSlate500
                )
            }

            if (logs.isNotEmpty()) {
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.testTag("clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear All Logs",
                        tint = MutedSlate500
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips (De-cluttered, clean horizontal list)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to "All Activity",
                "SECURITY" to "🚨 Security",
                "ACTIONS" to "👤 Actions",
                "SYSTEM" to "⚙️ System"
            ).forEach { (key, label) ->
                val isSelected = selectedFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = key },
                    label = {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SkyBluePrimary,
                        selectedLabelColor = Color.White,
                        containerColor = WhitePure,
                        labelColor = DeepSlate800
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = LightBlue100,
                        selectedBorderColor = SkyBluePrimary,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WhitePure),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFECFDF5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "No Incidents",
                                tint = EmeraldSafe,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "No Activity Recorded",
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate800,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Security triggers, user actions, and system logs will appear here in real time.",
                            color = MutedSlate500,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("intruder_logs_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredLogs, key = { it.id }) { item ->
                    IntruderLogItemCard(
                        log = item,
                        formattedTime = timeFormat.format(Date(item.timestamp)),
                        onDelete = { onDeleteLog(item.id) }
                    )
                }
            }
        }
    }

    // Confirmation dialog to clear logs
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "Clear Activity History?",
                    color = DeepSlate800,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will remove all recorded security incidents, user remote actions, and snapshots from this device.",
                    color = MutedSlate500
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllLogs()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CrimsonAlert)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = MutedSlate500)
                }
            },
            containerColor = WhitePure,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun IntruderLogItemCard(
    log: IntruderLogEntity,
    formattedTime: String,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val severityColor = when (log.severity) {
        "ALERT" -> CrimsonAlert
        "WARNING" -> AmberWarning
        else -> SkyBluePrimary
    }

    val iconVector = when {
        log.eventType.contains("Lock", ignoreCase = true) -> Icons.Default.Lock
        log.eventType.contains("Voice", ignoreCase = true) -> Icons.Default.RecordVoiceOver
        log.eventType.contains("Alarm", ignoreCase = true) || log.eventType.contains("Siren", ignoreCase = true) -> Icons.Default.Notifications
        log.eventType.contains("Snapshot", ignoreCase = true) -> Icons.Default.CameraAlt
        log.category == "USER_ACTION" -> Icons.Default.Person
        log.category == "SYSTEM" -> Icons.Default.Settings
        else -> Icons.Default.Warning
    }

    val categoryBadge = when (log.category) {
        "USER_ACTION" -> "USER ACTION"
        "SYSTEM" -> "SYSTEM"
        else -> "SECURITY"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .testTag("log_item_${log.id}"),
        colors = CardDefaults.cardColors(containerColor = WhitePure),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Icon, Event Name, Category Badge, Severity Pill, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when (log.severity) {
                                    "ALERT" -> Color(0xFFFFF1F2)
                                    "WARNING" -> Color(0xFFFEF3C7)
                                    else -> LightBlueSoft
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = severityColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = log.eventType,
                                fontWeight = FontWeight.Bold,
                                color = DeepSlate800,
                                fontSize = 14.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LightBlueSoft)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = categoryBadge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SkyBlueDark
                                )
                            }
                        }
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            color = MutedSlate500
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (log.severity) {
                                    "ALERT" -> Color(0xFFFFF1F2)
                                    "WARNING" -> Color(0xFFFEF3C7)
                                    else -> LightBlueSoft
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = log.severity,
                            color = severityColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Event",
                            tint = MutedSlate500.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = log.description,
                color = MutedSlate500,
                fontSize = 12.sp
            )

            // Warning issued pill if any
            if (!log.warningIssued.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LightBlueSoft)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Warning: \"${log.warningIssued}\"",
                        color = DeepSlate800,
                        fontSize = 11.sp
                    )
                }
            }

            // Snapshot preview if URL present
            if (!log.snapshotUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(log.snapshotUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Intruder Snapshot",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, LightBlue100, RoundedCornerShape(10.dp))
                )
            }
        }
    }
}
