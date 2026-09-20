package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AmberLight
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBorderLight
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonLight
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlueSoft
import com.example.ui.theme.MutedSlate500
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WhitePure
import com.example.ui.theme.WhiteSmoke
import com.example.ui.theme.WhiteSmokeAlt
import com.example.util.rememberAppHaptics
import com.example.viewmodel.ConnectionState
import com.example.viewmodel.MonitorUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveCameraScreen(
    uiState: MonitorUiState,
    cameraUrl: String?,
    onCaptureSnapshot: () -> Unit,
    onLockToggle: () -> Unit,
    onRefreshCamera: () -> Unit,
    onToggleAutoStream: () -> Unit,
    onToggleSubjectRecording: () -> Unit,
    onToggleAutoRecordOnMotion: () -> Unit,
    onToggleAutoSnapOnMotion: () -> Unit,
    onSendVoiceWarning: (String) -> Unit,
    onNavigateToIntercom: () -> Unit,
    onSetStreamQuality: (String) -> Unit = {},
    autoRecordOnMotion: Boolean,
    autoSnapOnMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDemo = uiState.connectionState == ConnectionState.DEMO_MODE

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val currentTime = remember(uiState.cameraRefreshTrigger) { timeFormatter.format(Date()) }

    // Pulsing REC Dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "camPulse")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )

    // Radar scanline animation for surveillance canvas
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanY"
    )

    val haptics = rememberAppHaptics()
    var activeCameraTab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WhiteSmoke)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ==========================================
        // 1. Live Camera Preview Card with Video HUD
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.2.dp,
                    if (uiState.motionAlertActive || uiState.isSubjectRecordingActive) CrimsonAlert else LightBlue100,
                    RoundedCornerShape(12.dp)
                )
                .testTag("camera_feed_container"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isDemo && cameraUrl != null) {
                    // Live camera stream / frame from laptop webcam
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("$cameraUrl?t=${uiState.cameraRefreshTrigger}")
                            .crossfade(true)
                            .build(),
                        contentDescription = "Laptop Live Camera Feed",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Surveillance Radar / Viewfinder Canvas
                    SurveillanceSimulationCanvas(
                        scanProgress = scanY,
                        motionActive = uiState.motionAlertActive || uiState.isSubjectRecordingActive,
                        subjectDetected = uiState.subjectDetected || uiState.motionAlertActive
                    )
                }

                // Surveillance HUD Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Top HUD: Status, Camera Identifier, Latency Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: REC / STREAM status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isRecording = uiState.isSubjectRecordingActive
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecording) CrimsonAlert
                                        else if (uiState.isAutoStreamingEnabled) EmeraldSafe.copy(alpha = recAlpha)
                                        else AmberWarning
                                    )
                            )
                            Text(
                                text = if (isRecording) "REC 00:${uiState.subjectRecordingDurationSec.toString().padStart(2, '0')}"
                                else if (uiState.isAutoStreamingEnabled) "LIVE STREAM"
                                else "MANUAL PREVIEW",
                                color = if (isRecording) CrimsonAlert else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Center: Camera Name
                        Text(
                            text = "CAM 01 // HD",
                            color = Color(0xFF7DD3FC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Right: Latency & Timestamp
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.networkLatencyMs?.let { latency ->
                                Text(
                                    text = "${latency}ms",
                                    color = if (latency < 60) EmeraldSafe else AmberWarning,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = currentTime,
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Center Overlay: Subject Detection Target Bounding Box
                    if (uiState.subjectDetected || uiState.motionAlertActive || uiState.isSubjectRecordingActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(110.dp)
                                .border(1.2.dp, CrimsonAlert, RoundedCornerShape(6.dp))
                                .padding(4.dp)
                        ) {
                            Text(
                                text = if (uiState.isSubjectRecordingActive) "RECORDING SUBJECT" else "SUBJECT DETECTED",
                                color = CrimsonAlert,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xFF0F172A).copy(alpha = 0.8f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Bottom HUD: Transport, FPS & Lock Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDemo) "FEED: DEMO SIMULATOR" else "LINK: ${uiState.activeTransport} (HD)",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        if (uiState.isLocked) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CrimsonAlert.copy(alpha = 0.9f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "LAPTOP LOCKED",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(EmeraldSafe.copy(alpha = 0.9f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Armed",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "GUARD ARMED",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. Primary Quick-Action Control Console
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LightBlue100),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Take Manual Snapshot Button
                Button(
                    onClick = {
                        haptics.tick()
                        onCaptureSnapshot()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("snapshot_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightBlueSoft,
                        contentColor = SkyBlueDark
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Take Photo",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Record Subject Video (Manual Trigger) Button
                val isRecording = uiState.isSubjectRecordingActive
                Button(
                    onClick = {
                        haptics.click()
                        onToggleSubjectRecording()
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(34.dp)
                        .testTag("record_subject_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) CrimsonAlert else DeepSlate800,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                        contentDescription = "Record Video",
                        tint = if (isRecording) Color.White else CrimsonAlert,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRecording) "Stop (${uiState.subjectRecordingDurationSec}s)" else "Record Clip",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quick Lock / Unlock Screen Button
                Button(
                    onClick = {
                        haptics.lockToggle(uiState.isLocked)
                        onLockToggle()
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(34.dp)
                        .testTag("lock_toggle_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isLocked) AmberWarning else SkyBluePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Toggle Lock",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.isLocked) "Unlock" else "Lock",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live Stream Polling Toggle Button
                IconButton(
                    onClick = {
                        haptics.tick()
                        onToggleAutoStream()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (uiState.isAutoStreamingEnabled) LightBlueSoft else WhiteSmokeAlt)
                        .border(1.dp, if (uiState.isAutoStreamingEnabled) SkyBluePrimary else LightBlue100, RoundedCornerShape(8.dp))
                        .testTag("toggle_stream_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isAutoStreamingEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Live Stream",
                        tint = if (uiState.isAutoStreamingEnabled) SkyBluePrimary else DeepSlate800,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ==========================================
        // 3. Compact Tabbed Control Deck (No scrolling)
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LightBlue100),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0 to "Voice Warnings",
                        1 to "Evidence (${uiState.recentSnapshots.size})",
                        2 to "Stream & Rules"
                    ).forEach { (idx, label) ->
                        val isSelected = activeCameraTab == idx
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
                                    activeCameraTab = idx
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DeepSlate800,
                                maxLines = 1
                            )
                        }
                    }
                }

                when (activeCameraTab) {
                    0 -> {
                        // Quick Voice Warnings & Intercom
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    haptics.click()
                                    onSendVoiceWarning("Step away from this computer! You are being recorded!")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonLight,
                                    contentColor = CrimsonAlert
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("📢 \"Step Away!\"", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    haptics.click()
                                    onSendVoiceWarning("Security alert! Intruder detected on camera.")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberLight,
                                    contentColor = AmberWarning
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("⚠️ \"Alert!\"", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    haptics.click()
                                    onSendVoiceWarning("Warning: Computer is locked and alarm is active.")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LightBlueSoft,
                                    contentColor = SkyBlueDark
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("🔒 \"Locked\"", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    haptics.click()
                                    onNavigateToIntercom()
                                },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, SkyBluePrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = SkyBluePrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Intercom", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SkyBluePrimary)
                            }
                        }
                    }
                    1 -> {
                        // Evidence Snapshots
                        if (uiState.recentSnapshots.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No captured photos yet. Tap \"Photo\" above to snap evidence.",
                                    fontSize = 11.sp,
                                    color = MutedSlate500
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.recentSnapshots.forEachIndexed { index, snapUrl ->
                                    Card(
                                        modifier = Modifier
                                            .size(width = 76.dp, height = 54.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, LightBlue100, RoundedCornerShape(6.dp)),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(snapUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Captured Snapshot #${index + 1}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Text(
                                                text = "#${index + 1}",
                                                fontSize = 9.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .background(Color.Black.copy(alpha = 0.6f))
                                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Stream Quality & Automation Rules
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Resolution:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MutedSlate500
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        "ECO" to "360p",
                                        "BALANCED" to "540p",
                                        "ULTRA" to "720p"
                                    ).forEach { (preset, label) ->
                                        val isSelected = uiState.streamQualityPreset == preset
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSelected) SkyBluePrimary else WhiteSmokeAlt)
                                                .clickable { onSetStreamQuality(preset) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) WhitePure else DeepSlate800
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Auto-Snap Motion",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DeepSlate800
                                    )
                                    Switch(
                                        checked = autoSnapOnMotion,
                                        onCheckedChange = {
                                            haptics.tick()
                                            onToggleAutoSnapOnMotion()
                                        },
                                        modifier = Modifier
                                            .scale(0.7f)
                                            .testTag("switch_auto_snap")
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Auto-Record Clip",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DeepSlate800
                                    )
                                    Switch(
                                        checked = autoRecordOnMotion,
                                        onCheckedChange = {
                                            haptics.tick()
                                            onToggleAutoRecordOnMotion()
                                        },
                                        modifier = Modifier
                                            .scale(0.7f)
                                            .testTag("switch_auto_record")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        content()
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MutedSlate500)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeepSlate800
        )
    }
}

@Composable
private fun SurveillanceSimulationCanvas(
    scanProgress: Float,
    motionActive: Boolean,
    subjectDetected: Boolean
) {
    val gridColor = CardBorderLight
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Grid lines
        val gridSpacing = 40.dp.toPx()
        var x = 0f
        while (x < width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += gridSpacing
        }

        var y = 0f
        while (y < height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += gridSpacing
        }

        // Radar circles
        val center = Offset(width / 2f, height / 2f)
        val maxRadius = (minOf(width, height) / 2f) * 0.85f

        drawCircle(
            color = Color(0xFF0284C7).copy(alpha = 0.2f),
            radius = maxRadius,
            center = center,
            style = Stroke(width = 1.5f)
        )
        drawCircle(
            color = Color(0xFF0284C7).copy(alpha = 0.15f),
            radius = maxRadius * 0.6f,
            center = center,
            style = Stroke(width = 1.5f)
        )

        // Center reticle
        val reticleSize = 24.dp.toPx()
        val reticleColor = if (motionActive) CrimsonAlert else Color(0xFF38BDF8)
        drawLine(
            color = reticleColor,
            start = Offset(center.x - reticleSize, center.y),
            end = Offset(center.x + reticleSize, center.y),
            strokeWidth = 2f
        )
        drawLine(
            color = reticleColor,
            start = Offset(center.x, center.y - reticleSize),
            end = Offset(center.x, center.y + reticleSize),
            strokeWidth = 2f
        )

        // Radar scan line sweep
        val scanYPos = height * scanProgress
        drawLine(
            color = if (motionActive) CrimsonAlert.copy(alpha = 0.6f) else Color(0xFF38BDF8).copy(alpha = 0.5f),
            start = Offset(0f, scanYPos),
            end = Offset(width, scanYPos),
            strokeWidth = 3f
        )

        // Corner viewfinder brackets
        val bracketLen = 28.dp.toPx()
        val p = 14.dp.toPx()
        val bracketColor = if (motionActive) CrimsonAlert else Color(0xFF38BDF8)

        // Top Left
        drawLine(bracketColor, Offset(p, p), Offset(p + bracketLen, p), strokeWidth = 2f)
        drawLine(bracketColor, Offset(p, p), Offset(p, p + bracketLen), strokeWidth = 2f)

        // Top Right
        drawLine(bracketColor, Offset(width - p, p), Offset(width - p - bracketLen, p), strokeWidth = 2f)
        drawLine(bracketColor, Offset(width - p, p), Offset(width - p, p + bracketLen), strokeWidth = 2f)

        // Bottom Left
        drawLine(bracketColor, Offset(p, height - p), Offset(p + bracketLen, height - p), strokeWidth = 2f)
        drawLine(bracketColor, Offset(p, height - p), Offset(p, height - p - bracketLen), strokeWidth = 2f)

        // Bottom Right
        drawLine(bracketColor, Offset(width - p, height - p), Offset(width - p - bracketLen, height - p), strokeWidth = 2f)
        drawLine(bracketColor, Offset(width - p, height - p), Offset(width - p, height - p - bracketLen), strokeWidth = 2f)
    }
}
