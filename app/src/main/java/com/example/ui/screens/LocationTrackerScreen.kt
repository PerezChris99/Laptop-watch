package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LaptopLocationEntity
import com.example.data.SubjectProfileEntity
import com.example.ui.components.OpenStreetMapContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBorderHover
import com.example.ui.theme.CardBorderLight
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonLight
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LightBlueContainer
import com.example.ui.theme.LightBluePrimary
import com.example.ui.theme.LightBlueSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.WhiteSmoke
import com.example.viewmodel.LaptopMonitorViewModel
import com.example.viewmodel.MonitorUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LocationTrackerScreen(
    viewModel: LaptopMonitorViewModel,
    uiState: MonitorUiState,
    locationHistory: List<LaptopLocationEntity>,
    subjectProfiles: List<SubjectProfileEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableStateOf("MAP") } // "MAP", "TRAIL", "BIOMETRICS", "TAMPER"
    var selectedProfileForEdit by remember { mutableStateOf<SubjectProfileEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WhiteSmoke)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
    ) {
        // 1. Header Banner: Live Coordinates & Refresh
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderLight))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (uiState.isOutsideGeofence) CrimsonAlert else EmeraldSafe)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.isOutsideGeofence) "PERIMETER BREACH" else "GPS HARDWARE TRACKING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isOutsideGeofence) CrimsonAlert else LightBluePrimary,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Live Device Location",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }

                        Button(
                            onClick = { viewModel.fetchLiveGpsCoordinates() },
                            colors = ButtonDefaults.buttonColors(containerColor = LightBlueSoft, contentColor = LightBluePrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("refresh_gps_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh GPS", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update GPS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // GPS Coordinates Box (Exact latitude & longitude)
                    Surface(
                        color = LightBlueSoft,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderLight)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "EXACT GPS COORDINATES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate500
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "%.7f, %.7f".format(uiState.currentLatitude, uiState.currentLongitude),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Slate900
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = uiState.locationAddress,
                                    fontSize = 12.sp,
                                    color = Slate600
                                )
                            }

                            IconButton(
                                onClick = {
                                    val geoUri = Uri.parse("geo:${uiState.currentLatitude},${uiState.currentLongitude}?q=${uiState.currentLatitude},${uiState.currentLongitude}(Laptop+Location+1m+Accurate)")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                    context.startActivity(Intent.createChooser(mapIntent, "Open Coordinates In"))
                                }
                            ) {
                                Icon(
                                    Icons.Default.Navigation,
                                    contentDescription = "Navigate to Coordinates",
                                    tint = LightBluePrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Accuracy & Last Seen Metadata Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LocationStatBadge(
                            icon = Icons.Default.GpsFixed,
                            label = "Accuracy",
                            value = "±%.1fm (1m RTK)".format(uiState.gpsAccuracyMeters),
                            modifier = Modifier.weight(1f)
                        )
                        LocationStatBadge(
                            icon = Icons.Default.Speed,
                            label = "Speed",
                            value = "%.1f km/h".format(uiState.gpsSpeedKmh),
                            modifier = Modifier.weight(1f)
                        )
                        LocationStatBadge(
                            icon = Icons.Default.AltRoute,
                            label = "Network",
                            value = uiState.activeTransport,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Sub-Category Tabs (Map / GPS Trail / Face Dossiers / Hardware Tamper)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "MAP" to "OpenMap",
                    "TRAIL" to "Last Seen (${locationHistory.size})",
                    "BIOMETRICS" to "Face Dossiers (${subjectProfiles.size})",
                    "TAMPER" to "Tamper"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedSubTab == key,
                        onClick = { selectedSubTab = key },
                        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LightBluePrimary,
                            selectedLabelColor = PureWhite,
                            containerColor = PureWhite,
                            labelColor = Slate600
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedSubTab == key,
                            borderColor = if (selectedSubTab == key) LightBluePrimary else CardBorderLight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // 3. Tab Content View
        when (selectedSubTab) {
            "MAP" -> {
                // Interactive OpenStreetMap Canvas
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OpenStreetMapContainer(
                            latitude = uiState.currentLatitude,
                            longitude = uiState.currentLongitude,
                            accuracyMeters = uiState.gpsAccuracyMeters,
                            isLiveFix = true,
                            isGeofenceArmed = uiState.isGeofenceArmed,
                            geofenceRadiusMeters = uiState.geofenceRadiusMeters,
                            locationHistory = locationHistory
                        )

                        // Geofence Perimeter Controls Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(16.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderLight))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            tint = if (uiState.isGeofenceArmed) LightBluePrimary else Slate400,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Virtual GPS Geofence (60m)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Slate900
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (uiState.isGeofenceArmed)
                                            "Boundary armed. Alerts & locks laptop if moved away."
                                        else
                                            "Geofence is currently disarmed.",
                                        fontSize = 12.sp,
                                        color = Slate500
                                    )
                                }

                                Switch(
                                    checked = uiState.isGeofenceArmed,
                                    onCheckedChange = { viewModel.toggleGeofence() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PureWhite,
                                        checkedTrackColor = LightBluePrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            "TRAIL" -> {
                // Historical GPS Breadcrumb Fixes (Works across LAN & different networks)
                item {
                    Text(
                        text = "GPS Coordinates History & Last Seen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Slate900
                    )
                    Text(
                        text = "Tracks device movement across Wi-Fi networks and cellular relays",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }

                if (locationHistory.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No historical GPS fixes recorded yet.", color = Slate400, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(locationHistory) { loc ->
                        GpsBreadcrumbCard(location = loc)
                    }
                }
            }

            "BIOMETRICS" -> {
                // Face Profiling & Behavior Dossiers
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        shape = RoundedCornerShape(16.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderLight))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = LightBluePrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Biometric Subject Fingerprinting",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Categorizes faces around the gadget, tracking repeat visits, posture, and dwell time.",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }
                }

                items(subjectProfiles) { profile ->
                    SubjectDossierCard(
                        profile = profile,
                        onClick = { selectedProfileForEdit = profile }
                    )
                }
            }

            "TAMPER" -> {
                // Hardware Tamper Detection Suite
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(16.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderLight))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Hardware Tamper Defense Suite",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Detects physical theft vectors: lid tampering, power line severing, or malicious USB peripherals.",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                            }
                        }

                        TamperActionCard(
                            title = "Lid Manipulation Sensor",
                            description = "Triggered when laptop lid is closed, lifted, or carried away while unattended.",
                            status = if (uiState.isLidClosedTamperDetected) "TAMPER DETECTED" else "ARMED (NORMAL)",
                            isTriggered = uiState.isLidClosedTamperDetected,
                            icon = Icons.Default.Lock,
                            onSimulate = { viewModel.triggerTamperSimulation("LID_TAMPER") }
                        )

                        TamperActionCard(
                            title = "AC Power Disconnect Alert",
                            description = "Instant alert when power cord is unplugged, indicating someone picking up the laptop.",
                            status = if (uiState.isPowerDisconnectTamperDetected) "UNPLUGGED / DISCONNECTED" else "POWER CONNECTED",
                            isTriggered = uiState.isPowerDisconnectTamperDetected,
                            icon = Icons.Default.PowerOff,
                            onSimulate = { viewModel.triggerTamperSimulation("POWER_TAMPER") }
                        )

                        TamperActionCard(
                            title = "USB Peripheral Port Watchdog",
                            description = "Detects unauthorized flash drives or Rubber Ducky injection devices inserted while locked.",
                            status = if (uiState.isPeripheralTamperDetected) "UNAUTHORIZED USB DETECTED" else "PORTS SECURED",
                            isTriggered = uiState.isPeripheralTamperDetected,
                            icon = Icons.Default.Usb,
                            onSimulate = { viewModel.triggerTamperSimulation("PERIPHERAL_TAMPER") }
                        )
                    }
                }
            }
        }
    }

    // Biometric Dossier Classification Dialog
    selectedProfileForEdit?.let { profile ->
        AlertDialog(
            onDismissRequest = { selectedProfileForEdit = null },
            title = { Text("Classify Biometric Dossier", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Subject: ${profile.displayName} (${profile.subjectTag})")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Current notes: ${profile.behaviorNotes}", fontSize = 12.sp, color = Slate500)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Select Trust Level:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.updateSubjectClassification(profile.id, "AUTHORIZED", "Verified trusted user.")
                                selectedProfileForEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Authorize", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.updateSubjectClassification(profile.id, "INVESTIGATE", "Marked for surveillance.")
                                selectedProfileForEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Investigate", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.updateSubjectClassification(profile.id, "FLAGGED_THREAT", "Flagged intruder threat.")
                                selectedProfileForEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Threat", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProfileForEdit = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun LocationStatBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = WhiteSmoke,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = LightBluePrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(text = label, fontSize = 9.sp, color = Slate400, fontWeight = FontWeight.Bold)
                Text(text = value, fontSize = 12.sp, color = Slate800, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GpsBreadcrumbCard(location: LaptopLocationEntity) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(location.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderLight)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (location.isLiveFix) LightBlueContainer else WhiteSmoke),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (location.isLiveFix) Icons.Default.GpsFixed else Icons.Default.History,
                        contentDescription = null,
                        tint = if (location.isLiveFix) LightBluePrimary else Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "%.5f, %.5f".format(location.latitude, location.longitude),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Slate900
                        )
                        if (location.isLiveFix) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = EmeraldLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "LIVE FIX",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSafe,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = location.addressEstimate, fontSize = 12.sp, color = Slate600)
                    Text(
                        text = "$dateStr • Accuracy: ±${location.accuracyMeters}m • ${location.transport}",
                        fontSize = 10.sp,
                        color = Slate400
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectDossierCard(
    profile: SubjectProfileEntity,
    onClick: () -> Unit
) {
    val categoryColor = when (profile.securityCategory) {
        "AUTHORIZED" -> EmeraldSafe
        "INVESTIGATE" -> AmberWarning
        else -> CrimsonAlert
    }
    val categoryBg = when (profile.securityCategory) {
        "AUTHORIZED" -> EmeraldLight
        "INVESTIGATE" -> AmberWarning.copy(alpha = 0.15f)
        else -> CrimsonLight
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderLight)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(categoryBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null, tint = categoryColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = profile.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate900)
                        Text(text = profile.subjectTag, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate400)
                    }
                }

                Surface(
                    color = categoryBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = profile.securityCategory,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(text = profile.behaviorNotes, fontSize = 12.sp, color = Slate600)

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = CardBorderLight)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Sightings: ${profile.encounterCount} times", fontSize = 11.sp, color = Slate500)
                Text(text = "Confidence: ${(profile.confidenceScore * 100).toInt()}%", fontSize = 11.sp, color = Slate500)
            }
        }
    }
}

@Composable
private fun TamperActionCard(
    title: String,
    description: String,
    status: String,
    isTriggered: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSimulate: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isTriggered) CrimsonAlert else CardBorderLight)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isTriggered) CrimsonLight else LightBlueSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = if (isTriggered) CrimsonAlert else LightBluePrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                        Text(
                            text = status,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isTriggered) CrimsonAlert else EmeraldSafe
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSimulate,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Test Trigger", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = description, fontSize = 12.sp, color = Slate500)
        }
    }
}
