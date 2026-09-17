package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DiagnosticEvent
import com.example.data.IntruderLogEntity
import com.example.data.LaptopConfigEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlue50
import com.example.ui.theme.LightBlueSoft
import com.example.ui.theme.MutedSlate500
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WhitePure
import com.example.ui.theme.WhiteSmoke
import com.example.util.rememberAppHaptics
import com.example.viewmodel.MonitorTab
import com.example.viewmodel.MonitorUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertDashboardScreen(
    uiState: MonitorUiState,
    config: LaptopConfigEntity?,
    intruderLogs: List<IntruderLogEntity>,
    onToggleMotionArmed: () -> Unit,
    onSetSensitivity: (String) -> Unit,
    onToggleAutoLock: () -> Unit,
    onToggleAutoAlarm: () -> Unit,
    onToggleAutoSnap: () -> Unit,
    onToggleAutoTts: () -> Unit,
    onSimulateMotion: () -> Unit,
    onDismissAlert: () -> Unit,
    onLockLaptop: () -> Unit,
    onUnlockLaptop: () -> Unit,
    onTriggerAlarm: () -> Unit,
    onNavigateToTab: (MonitorTab) -> Unit,
    onToggleAwayMode: () -> Unit = {},
    onSetAwaySensitivity: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDiagnosticsDetails by remember { mutableStateOf(false) }
    var selectedDashboardSection by remember { mutableStateOf(0) } // 0: Overview, 1: Detection & Rules, 2: System Health

    val haptics = rememberAppHaptics()
    val infiniteTransition = rememberInfiniteTransition(label = "alertPulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val isArmed = config?.isMotionArmed ?: true
    val sensitivity = config?.motionSensitivity ?: "MEDIUM"
    val threatLevel = uiState.threatLevel

    val statusColor by animateColorAsState(
        targetValue = when (threatLevel) {
            "ALERT" -> CrimsonAlert
            "ELEVATED" -> AmberWarning
            else -> if (isArmed) EmeraldSafe else MutedSlate500
        },
        label = "statusColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WhiteSmoke)
            .testTag("alert_dashboard_screen")
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. MASTER DEFENSE & THREAT LEVEL STATUS CARD
        ThreatLevelBannerCard(
            threatLevel = threatLevel,
            isArmed = isArmed,
            isLocked = uiState.isLocked,
            isAwayMode = uiState.isAwayMode,
            statusColor = statusColor,
            pulseGlow = pulseGlow,
            motionAlertActive = uiState.motionAlertActive,
            onDismissAlert = {
                haptics.tick()
                onDismissAlert()
            },
            onToggleMotionArmed = {
                haptics.click()
                onToggleMotionArmed()
            },
            onLockLaptop = {
                haptics.lockToggle(false)
                onLockLaptop()
            },
            onUnlockLaptop = {
                haptics.lockToggle(true)
                onUnlockLaptop()
            },
            onTriggerAlarm = {
                haptics.alarmWarning()
                onTriggerAlarm()
            }
        )

        // 2. SECTION SELECTOR TABS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val sections = listOf(
                    Triple(0, "Overview", Icons.Default.Shield),
                    Triple(1, "Radar & Rules", Icons.Default.Tune),
                    Triple(2, "Diagnostics", Icons.Default.BugReport)
                )
                sections.forEach { (index, title, icon) ->
                    val isSelected = selectedDashboardSection == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) SkyBluePrimary else Color.Transparent)
                            .clickable {
                                haptics.tick()
                                selectedDashboardSection = index
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else MutedSlate500,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DeepSlate800
                            )
                        }
                    }
                }
            }
        }

        // 3. MAIN SECTION CONTENT (Fits viewport without scrolling)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedDashboardSection) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick Metrics Strip
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = WhitePure),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "RADAR MOTION",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MutedSlate500
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "${uiState.motionIntensity}% intensity",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.motionIntensity > 20) CrimsonAlert else DeepSlate800
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(LightBlue100)
                                )

                                Column {
                                    Text(
                                        text = "POLICY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MutedSlate500
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = if (config?.autoLockOnMotion == true) "Auto-Lock" else "Manual",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SkyBlueDark
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(LightBlue100)
                                )

                                Column {
                                    Text(
                                        text = "WATCHDOG",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MutedSlate500
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "Normal",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSafe
                                    )
                                }
                            }
                        }

                        // Quick access to Radar & Rules
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(LightBlueSoft)
                                .border(0.8.dp, LightBlue100, RoundedCornerShape(8.dp))
                                .clickable { selectedDashboardSection = 1 }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = SkyBluePrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Sensitivity: $sensitivity • Rules Active: 4",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepSlate800
                                )
                            }
                            Text(
                                text = "Customize →",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SkyBluePrimary
                            )
                        }

                        // Recent Incidents Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECENT SECURITY EVENTS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SkyBlueDark,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "View All (${intruderLogs.size}) →",
                                fontSize = 11.sp,
                                color = SkyBluePrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToTab(MonitorTab.LOGS) }
                            )
                        }

                        if (intruderLogs.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                colors = CardDefaults.cardColors(containerColor = WhitePure),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = EmeraldSafe,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "Zero Intrusion Events Recorded",
                                            color = DeepSlate800,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Your laptop workspace is safe and guarded.",
                                            color = MutedSlate500,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val displayLogs = intruderLogs.take(2)
                                displayLogs.forEach { log ->
                                    IncidentAlertItem(
                                        log = log,
                                        onViewFeed = { onNavigateToTab(MonitorTab.CAMERA) },
                                        onIntercom = { onNavigateToTab(MonitorTab.INTERCOM) }
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MotionRadarControlCard(
                            isArmed = isArmed,
                            sensitivity = sensitivity,
                            isAwayMode = uiState.isAwayMode,
                            awaySensitivity = uiState.awaySensitivity,
                            motionIntensity = uiState.motionIntensity,
                            motionAlertActive = uiState.motionAlertActive,
                            onSetSensitivity = onSetSensitivity,
                            onToggleAwayMode = onToggleAwayMode,
                            onSetAwaySensitivity = onSetAwaySensitivity,
                            onSimulateMotion = onSimulateMotion
                        )

                        DefenseAutomationRulesCard(
                            config = config,
                            onToggleAutoLock = onToggleAutoLock,
                            onToggleAutoAlarm = onToggleAutoAlarm,
                            onToggleAutoSnap = onToggleAutoSnap,
                            onToggleAutoTts = onToggleAutoTts
                        )
                    }
                }

                2 -> {
                    SystemHealthWatchdogCard(
                        uiState = uiState,
                        showDetails = showDiagnosticsDetails,
                        onToggleDetails = { showDiagnosticsDetails = !showDiagnosticsDetails }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreatLevelBannerCard(
    threatLevel: String,
    isArmed: Boolean,
    isLocked: Boolean,
    isAwayMode: Boolean,
    statusColor: Color,
    pulseGlow: Float,
    motionAlertActive: Boolean,
    onDismissAlert: () -> Unit,
    onToggleMotionArmed: () -> Unit,
    onLockLaptop: () -> Unit,
    onUnlockLaptop: () -> Unit,
    onTriggerAlarm: () -> Unit
) {
    val containerBg = when (threatLevel) {
        "ALERT" -> Color(0xFFFFF1F2)
        "ELEVATED" -> Color(0xFFFFFBEB)
        else -> if (isArmed) Color(0xFFF0FDF4) else WhitePure
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (motionAlertActive) CrimsonAlert.copy(alpha = pulseGlow) else LightBlue100,
                RoundedCornerShape(12.dp)
            )
            .testTag("threat_level_card"),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row with Shield and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f))
                            .border(1.dp, statusColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (threatLevel) {
                                "ALERT" -> Icons.Default.Warning
                                "ELEVATED" -> Icons.Default.Bolt
                                else -> Icons.Default.Shield
                            },
                            contentDescription = "Threat Level Icon",
                            tint = statusColor,
                            modifier = Modifier
                                .size(16.dp)
                                .then(if (motionAlertActive) Modifier.alpha(pulseGlow) else Modifier)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isArmed) "DEFENSE ARMED" else "DISARMED",
                                fontSize = 11.sp,
                                color = if (isArmed) EmeraldSafe else MutedSlate500,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• $threatLevel",
                                fontSize = 11.sp,
                                color = statusColor,
                                fontWeight = FontWeight.ExtraBold
                            )

                            if (isAwayMode) {
                                Text(
                                    text = "• AWAY",
                                    fontSize = 10.sp,
                                    color = Color(0xFFB45309),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = when (threatLevel) {
                                "ALERT" -> "⚠️ Active intruder motion detected!"
                                "ELEVATED" -> "Recent movement near laptop"
                                else -> if (isArmed) "Laptop workspace safe & guarded" else "Motion sensor is disarmed"
                            },
                            color = DeepSlate800,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                // If alert active, show dismiss button
                if (motionAlertActive) {
                    IconButton(
                        onClick = onDismissAlert,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFECDD3))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Alert",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Master Arm/Disarm Button
                OutlinedButton(
                    onClick = onToggleMotionArmed,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .testTag("arm_motion_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isArmed) EmeraldSafe else MutedSlate500
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isArmed) EmeraldSafe.copy(alpha = 0.5f) else LightBlue100
                    )
                ) {
                    Icon(
                        imageVector = if (isArmed) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isArmed) "ARMED" else "DISARM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                // Emergency Lockdown Button
                Button(
                    onClick = { if (isLocked) onUnlockLaptop() else onLockLaptop() },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(32.dp)
                        .testTag("dashboard_lock_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLocked) CrimsonAlert else SkyBluePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isLocked) "LOCKED" else "LOCK SCREEN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                // Deterrent Siren Alarm Button
                IconButton(
                    onClick = onTriggerAlarm,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF1F2))
                        .border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(8.dp))
                        .testTag("dashboard_alarm_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Trigger Siren",
                        tint = CrimsonAlert,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionRadarControlCard(
    isArmed: Boolean,
    sensitivity: String,
    isAwayMode: Boolean,
    awaySensitivity: String,
    motionIntensity: Int,
    motionAlertActive: Boolean,
    onSetSensitivity: (String) -> Unit,
    onToggleAwayMode: () -> Unit,
    onSetAwaySensitivity: (String) -> Unit,
    onSimulateMotion: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("motion_radar_card"),
        colors = CardDefaults.cardColors(containerColor = WhitePure),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MotionPhotosOn,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "MOTION DETECTION RADAR",
                        fontWeight = FontWeight.Bold,
                        color = DeepSlate800,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = if (isArmed) "ACTIVE (12 FPS)" else "STANDBY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isArmed) SkyBluePrimary else MutedSlate500
                )
            }

            // Live Motion Intensity Meter
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Webcam Motion Intensity",
                        fontSize = 11.sp,
                        color = MutedSlate500
                    )
                    Text(
                        text = "$motionIntensity%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (motionIntensity > 20) CrimsonAlert else SkyBlueDark
                    )
                }

                LinearProgressIndicator(
                    progress = { (motionIntensity / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (motionAlertActive) CrimsonAlert else SkyBluePrimary,
                    trackColor = LightBlueSoft
                )
            }

            // Standard Sensitivity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sensitivity:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedSlate500
                )

                listOf(
                    "LOW" to "Low (25%)",
                    "MEDIUM" to "Med (15%)",
                    "HIGH" to "High (5%)"
                ).forEach { (key, label) ->
                    val isSelected = sensitivity.equals(key, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) SkyBluePrimary else LightBlueSoft)
                            .border(
                                0.8.dp,
                                if (isSelected) SkyBluePrimary else LightBlue100,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                haptics.tick()
                                onSetSensitivity(key)
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else DeepSlate800
                        )
                    }
                }
            }

            // Simulate Motion Button
            Button(
                onClick = {
                    haptics.click()
                    onSimulateMotion()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .testTag("simulate_motion_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LightBlueSoft,
                    contentColor = SkyBlueDark
                ),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Simulate Motion (Instant Test)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DefenseAutomationRulesCard(
    config: LaptopConfigEntity?,
    onToggleAutoLock: () -> Unit,
    onToggleAutoAlarm: () -> Unit,
    onToggleAutoSnap: () -> Unit,
    onToggleAutoTts: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("defense_rules_card"),
        colors = CardDefaults.cardColors(containerColor = WhitePure),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AUTOMATED DEFENSE ON MOTION",
                    fontWeight = FontWeight.Bold,
                    color = DeepSlate800,
                    fontSize = 12.sp
                )
            }

            CompactRuleSwitchRow(
                icon = Icons.Default.Lock,
                title = "Auto-Lock Laptop Screen",
                checked = config?.autoLockOnMotion ?: true,
                onCheckedChange = { onToggleAutoLock() },
                testTag = "rule_autolock_switch"
            )

            HorizontalDivider(color = LightBlue100)

            CompactRuleSwitchRow(
                icon = Icons.Default.PhotoCamera,
                title = "Capture Intruder Photo",
                checked = config?.autoSnapOnMotion ?: true,
                onCheckedChange = { onToggleAutoSnap() },
                testTag = "rule_autosnap_switch"
            )

            HorizontalDivider(color = LightBlue100)

            CompactRuleSwitchRow(
                icon = Icons.Default.VolumeUp,
                title = "Broadcast Voice Warning",
                checked = config?.autoTtsOnMotion ?: false,
                onCheckedChange = { onToggleAutoTts() },
                testTag = "rule_autotts_switch"
            )

            HorizontalDivider(color = LightBlue100)

            CompactRuleSwitchRow(
                icon = Icons.Default.NotificationsActive,
                title = "Sound Deterrent Siren",
                checked = config?.autoAlarmOnMotion ?: false,
                onCheckedChange = { onToggleAutoAlarm() },
                testTag = "rule_autoalarm_switch"
            )
        }
    }
}

@Composable
private fun CompactRuleSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SkyBluePrimary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeepSlate800
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.tick()
                onCheckedChange(it)
            },
            modifier = Modifier
                .scale(0.75f)
                .testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = WhitePure,
                checkedTrackColor = SkyBluePrimary,
                uncheckedThumbColor = WhitePure,
                uncheckedTrackColor = LightBlue100
            )
        )
    }
}

@Composable
private fun RuleSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (checked) LightBlueSoft else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) SkyBluePrimary else MutedSlate500,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = DeepSlate800,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    color = MutedSlate500,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.tick()
                onCheckedChange(it)
            },
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SkyBluePrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = LightBlue100
            )
        )
    }
}

@Composable
private fun SystemHealthWatchdogCard(
    uiState: MonitorUiState,
    showDetails: Boolean,
    onToggleDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("system_health_card"),
        colors = CardDefaults.cardColors(containerColor = WhitePure),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = EmeraldSafe,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ERROR WATCHDOG & STABILITY",
                        fontWeight = FontWeight.Bold,
                        color = DeepSlate800,
                        fontSize = 14.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFECFDF5))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldSafe)
                    )
                    Text(
                        text = "HEALTHY",
                        color = EmeraldSafe,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subsystem Health Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthStatusPill(
                    name = "Room DB",
                    status = "ONLINE",
                    isGood = true,
                    modifier = Modifier.weight(1f)
                )
                HealthStatusPill(
                    name = "Audio Mic",
                    status = "READY",
                    isGood = true,
                    modifier = Modifier.weight(1f)
                )
                HealthStatusPill(
                    name = "Auto-Recovery",
                    status = "${uiState.caughtErrorsCount} Handled",
                    isGood = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleDetails() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Proactive Diagnostic Log (${uiState.recentDiagnostics.size} events)",
                    color = SkyBluePrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (uiState.recentDiagnostics.isEmpty()) {
                        Text(
                            text = "No diagnostic errors captured yet. Watchdog active.",
                            fontSize = 11.sp,
                            color = MutedSlate500
                        )
                    } else {
                        uiState.recentDiagnostics.take(6).forEach { diag ->
                            DiagnosticEventRow(diag)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthStatusPill(
    name: String,
    status: String,
    isGood: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LightBlueSoft)
            .border(1.dp, LightBlue100, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = name,
                fontSize = 10.sp,
                color = MutedSlate500
            )
            Text(
                text = status,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGood) EmeraldSafe else CrimsonAlert
            )
        }
    }
}

@Composable
private fun DiagnosticEventRow(event: DiagnosticEvent) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(event.timestamp) { timeFormatter.format(Date(event.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(LightBlueSoft)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeStr,
            fontSize = 10.sp,
            color = MutedSlate500,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "[${event.tag}]",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (event.status == "HEALTHY") EmeraldSafe else AmberWarning,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = event.message,
            fontSize = 11.sp,
            color = DeepSlate800,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IncidentAlertItem(
    log: IntruderLogEntity,
    onViewFeed: () -> Unit,
    onIntercom: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss • MMM dd", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { timeFormatter.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WhitePure),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
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
                        imageVector = when (log.eventType) {
                            "Motion Detected" -> Icons.Default.MotionPhotosOn
                            "Screen Locked" -> Icons.Default.Lock
                            "Voice Warning" -> Icons.Default.VolumeUp
                            else -> Icons.Default.CameraAlt
                        },
                        contentDescription = null,
                        tint = when (log.severity) {
                            "ALERT" -> CrimsonAlert
                            "WARNING" -> AmberWarning
                            else -> SkyBluePrimary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = log.eventType,
                        color = DeepSlate800,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = log.description,
                        color = MutedSlate500,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Text(
                        text = formattedTime,
                        color = MutedSlate500.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onViewFeed,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "View Feed",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onIntercom,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Intercom",
                        tint = AmberWarning,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
