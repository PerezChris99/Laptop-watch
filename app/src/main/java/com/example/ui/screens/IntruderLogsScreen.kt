package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.IntruderLogEntity
import com.example.ui.theme.AmberLight
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonLight
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldLight
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
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf("TIMELINE") } // "TIMELINE" or "CARDS"
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        val byCategory = when (selectedFilter) {
            "GPS" -> logs.filter { it.category == "GPS" || it.eventType.contains("GPS", ignoreCase = true) }
            "SECURITY" -> logs.filter { it.category == "SECURITY" || it.severity == "ALERT" }
            "ACTIONS" -> logs.filter { it.category == "USER_ACTION" }
            "SYSTEM" -> logs.filter { it.category == "SYSTEM" }
            else -> logs
        }
        if (searchQuery.isBlank()) {
            byCategory
        } else {
            byCategory.filter {
                it.eventType.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true) ||
                (it.warningIssued ?: "").contains(searchQuery, ignoreCase = true) ||
                (it.locationName ?: "").contains(searchQuery, ignoreCase = true)
            }
        }
    }

    fun generateTextReport(): String {
        val sb = StringBuilder()
        sb.appendLine("========================================")
        sb.appendLine("LAPTOP SENTINEL SECURITY LOG AUDIT")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("Total Incidents & Events: ${logs.size}")
        sb.appendLine("========================================\n")

        logs.forEachIndexed { index, item ->
            sb.appendLine("#${index + 1} [${timeFormat.format(Date(item.timestamp))}] - ${item.eventType}")
            sb.appendLine("  Severity: ${item.severity} | Category: ${item.category} | Locked: ${item.wasLocked}")
            sb.appendLine("  Description: ${item.description}")
            if (item.latitude != null && item.longitude != null) {
                sb.appendLine("  Location: %.7f, %.7f (±%.1fm 1-Meter GPS Accuracy)".format(item.latitude, item.longitude, item.accuracyMeters ?: 1.0f))
            }
            if (!item.warningIssued.isNullOrBlank()) {
                sb.appendLine("  Warning Issued: ${item.warningIssued}")
            }
            if (!item.snapshotUrl.isNullOrBlank()) {
                sb.appendLine("  Snapshot: ${item.snapshotUrl}")
            }
            sb.appendLine("----------------------------------------")
        }
        return sb.toString()
    }

    fun generateCsvReport(): String {
        val sb = StringBuilder()
        sb.appendLine("Timestamp,Date_Time,Event_Type,Severity,Category,Was_Locked,Latitude,Longitude,Accuracy_Meters,Description,Warning_Issued,Snapshot_URL")
        logs.forEach { item ->
            val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
            val cleanDesc = "\"${item.description.replace("\"", "\"\"")}\""
            val cleanWarn = "\"${(item.warningIssued ?: "").replace("\"", "\"\"")}\""
            val cleanSnap = "\"${(item.snapshotUrl ?: "").replace("\"", "\"\"")}\""
            val lat = item.latitude?.toString() ?: ""
            val lng = item.longitude?.toString() ?: ""
            val acc = item.accuracyMeters?.toString() ?: ""
            sb.appendLine("${item.timestamp},$formattedTime,${item.eventType},${item.severity},${item.category},${item.wasLocked},$lat,$lng,$acc,$cleanDesc,$cleanWarn,$cleanSnap")
        }
        return sb.toString()
    }

    fun shareReport(content: String, mimeType: String, title: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Security Audit Log")
        context.startActivity(shareIntent)
    }

    fun copyToClipboard(content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Laptop Security Logs", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WhiteSmoke)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top row: Header & Action buttons (Export + Clear)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Activity Timeline Log",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepSlate800
                )
                Text(
                    text = "${logs.size} sequential events • 1-Meter GPS Accuracy",
                    fontSize = 12.sp,
                    color = MutedSlate500
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (logs.isNotEmpty()) {
                    // Export Button
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(LightBlueSoft)
                            .testTag("export_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Logs",
                            tint = SkyBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Clear All Button
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CrimsonLight)
                            .testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All Logs",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // View Mode Toggle (Timeline vs Cards)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LightBlueSoft)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                onClick = { viewMode = "TIMELINE" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (viewMode == "TIMELINE") SkyBluePrimary else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = if (viewMode == "TIMELINE") Color.White else DeepSlate800,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Activity Timeline",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewMode == "TIMELINE") Color.White else DeepSlate800
                    )
                }
            }

            Surface(
                onClick = { viewMode = "CARDS" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (viewMode == "CARDS") SkyBluePrimary else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewAgenda,
                        contentDescription = null,
                        tint = if (viewMode == "CARDS") Color.White else DeepSlate800,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Incident Cards",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewMode == "CARDS") Color.White else DeepSlate800
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            placeholder = { Text("Search activity, GPS coordinates, actions...", fontSize = 12.sp, color = MutedSlate500) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedSlate500, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MutedSlate500, modifier = Modifier.size(16.dp))
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WhitePure,
                unfocusedContainerColor = WhitePure,
                focusedBorderColor = SkyBluePrimary,
                unfocusedBorderColor = LightBlue100
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "ALL" to "All",
                "GPS" to "📍 GPS (±1m)",
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
                            fontSize = 11.sp,
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

        Spacer(modifier = Modifier.height(10.dp))

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
                                .background(EmeraldLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "No Activity",
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
                            text = "Security triggers, 1-meter GPS updates, and tamper incidents will be logged sequentially in this timeline.",
                            color = MutedSlate500,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (viewMode == "TIMELINE") {
                // Render Activity Timeline Log
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("activity_timeline_list"),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { item ->
                        ActivityTimelineNode(
                            log = item,
                            formattedTime = timeFormat.format(Date(item.timestamp)),
                            onDelete = { onDeleteLog(item.id) },
                            onShare = {
                                shareReport(
                                    "LAPTOP SENTINEL TIMELINE EVENT\nEvent: ${item.eventType}\nTime: ${timeFormat.format(Date(item.timestamp))}\nCoordinates: ${item.latitude ?: 0.0}, ${item.longitude ?: 0.0} (±1m Accuracy)\nDescription: ${item.description}",
                                    "text/plain",
                                    "Security Timeline Event"
                                )
                            },
                            onCopyLocation = {
                                if (item.latitude != null && item.longitude != null) {
                                    val coordStr = "%.7f, %.7f (±%.1fm 1-Meter GPS Accuracy)".format(item.latitude, item.longitude, item.accuracyMeters ?: 1.0f)
                                    copyToClipboard(coordStr)
                                }
                            }
                        )
                    }
                }
            } else {
                // Render Detailed Cards View
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
                    "This will remove all recorded security incidents, 1-meter GPS tracks, user actions, and snapshots from this device.",
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

    // Export Logs Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Export Security Logs",
                        color = DeepSlate800,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Export ${logs.size} recorded incidents and activity timeline events with 1-meter GPS telemetry:",
                        color = MutedSlate500,
                        fontSize = 13.sp
                    )

                    // Option 1: Share Text Audit Report
                    Button(
                        onClick = {
                            shareReport(generateTextReport(), "text/plain", "Laptop Surveillance Audit Report")
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Formatted Audit Report", fontSize = 13.sp)
                    }

                    // Option 2: Export CSV
                    Button(
                        onClick = {
                            shareReport(generateCsvReport(), "text/csv", "laptop_security_logs.csv")
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlueDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as CSV Spreadsheet", fontSize = 13.sp)
                    }

                    // Option 3: Copy to Clipboard
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(generateTextReport())
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Full Report to Clipboard", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close", color = MutedSlate500)
                }
            },
            containerColor = WhitePure,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Visual chronological timeline node connecting sequential events with a vertical stem line.
 */
@Composable
private fun ActivityTimelineNode(
    log: IntruderLogEntity,
    formattedTime: String,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onCopyLocation: () -> Unit
) {
    val context = LocalContext.current
    val diffSec = (System.currentTimeMillis() - log.timestamp) / 1000
    val relativeTime = when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        else -> "${diffSec / 86400}d ago"
    }

    val (nodeColor, nodeIcon) = when {
        log.category == "GPS" || log.eventType.contains("GPS", ignoreCase = true) || log.eventType.contains("Geofence", ignoreCase = true) ->
            SkyBluePrimary to Icons.Default.GpsFixed
        log.eventType.contains("Tamper", ignoreCase = true) || log.eventType.contains("Lid", ignoreCase = true) || log.eventType.contains("Power", ignoreCase = true) ->
            AmberWarning to Icons.Default.PowerOff
        log.eventType.contains("Face", ignoreCase = true) || log.eventType.contains("Biometric", ignoreCase = true) ->
            Color(0xFF8B5CF6) to Icons.Default.Face
        log.eventType.contains("Lock", ignoreCase = true) ->
            (if (log.wasLocked) CrimsonAlert else EmeraldSafe) to Icons.Default.Lock
        log.eventType.contains("Voice", ignoreCase = true) ->
            SkyBlueDark to Icons.Default.RecordVoiceOver
        log.severity == "ALERT" ->
            CrimsonAlert to Icons.Default.Warning
        log.category == "USER_ACTION" ->
            EmeraldSafe to Icons.Default.Person
        else ->
            SkyBluePrimary to Icons.Default.Notifications
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Left timeline stem and circular node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            // Node circle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(nodeColor.copy(alpha = 0.15f))
                    .border(2.dp, nodeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = nodeIcon,
                    contentDescription = null,
                    tint = nodeColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            // Vertical connecting line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(LightBlue100)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right timeline content card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Top row: Event Title + Relative time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = log.eventType,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = DeepSlate800
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(nodeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = log.category,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = nodeColor
                            )
                        }
                    }

                    Text(
                        text = relativeTime,
                        fontSize = 11.sp,
                        color = MutedSlate500,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Exact timestamp
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    color = MutedSlate500,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Description
                Text(
                    text = log.description,
                    fontSize = 12.sp,
                    color = DeepSlate800,
                    lineHeight = 17.sp
                )

                // 1-Meter GPS Accuracy Tag
                if (log.latitude != null && log.longitude != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = LightBlueSoft,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    tint = SkyBluePrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Column {
                                    Text(
                                        text = "%.7f, %.7f".format(log.latitude, log.longitude),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepSlate800
                                    )
                                    Text(
                                        text = "±%.1fm 1-Meter GPS Accuracy • ${log.locationName ?: "Desk Workstation"}".format(log.accuracyMeters ?: 1.0f),
                                        fontSize = 10.sp,
                                        color = SkyBlueDark
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = onCopyLocation,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy Coordinates",
                                        tint = MutedSlate500,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val geoUri = Uri.parse("geo:${log.latitude},${log.longitude}?q=${log.latitude},${log.longitude}(1m+GPS+Location)")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                        context.startActivity(Intent.createChooser(mapIntent, "Open Pinpoint in Map"))
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Navigation,
                                        contentDescription = "Navigate",
                                        tint = SkyBluePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Spoken warning / intercom notification
                if (!log.warningIssued.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AmberLight)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(13.dp))
                        Text(text = "Announced: \"${log.warningIssued}\"", fontSize = 11.sp, color = DeepSlate800)
                    }
                }

                // Snapshot Thumbnail
                if (!log.snapshotUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(log.snapshotUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Intruder Snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, LightBlue100, RoundedCornerShape(8.dp))
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom actions: Share, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Event",
                            tint = MutedSlate500,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Event",
                            tint = CrimsonAlert.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
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
        log.category == "GPS" -> Icons.Default.GpsFixed
        log.category == "USER_ACTION" -> Icons.Default.Person
        log.category == "SYSTEM" -> Icons.Default.Settings
        else -> Icons.Default.Warning
    }

    val categoryBadge = when (log.category) {
        "USER_ACTION" -> "USER ACTION"
        "SYSTEM" -> "SYSTEM"
        "GPS" -> "GPS ±1M"
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
                                    "ALERT" -> CrimsonLight
                                    "WARNING" -> AmberLight
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
                            color = MutedSlate500,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Lock state indicator badge if applicable
                    if (log.wasLocked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CrimsonLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LOCKED",
                                color = CrimsonAlert,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Log",
                            tint = MutedSlate500,
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

            // 1-Meter GPS tag in Card view
            if (log.latitude != null && log.longitude != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(LightBlueSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.GpsFixed, contentDescription = null, tint = SkyBluePrimary, modifier = Modifier.size(13.dp))
                    Text(
                        text = "Lat: %.7f, Lng: %.7f (±%.1fm 1-Meter GPS Accuracy)".format(log.latitude, log.longitude, log.accuracyMeters ?: 1.0f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SkyBlueDark
                    )
                }
            }

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
