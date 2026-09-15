package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.Navy700
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.Slate400
import com.example.viewmodel.ConnectionState
import com.example.viewmodel.MonitorTab
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
    onNavigateToIntercom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDemo = uiState.connectionState == ConnectionState.DEMO_MODE

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val currentTime = remember(uiState.cameraRefreshTrigger) { timeFormatter.format(Date()) }

    // Pulsing REC Dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "camPulse")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )

    // Radar scanline animation for demo feed
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanY"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy900)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Feed Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, if (uiState.motionAlertActive) CrimsonAlert else Navy700, RoundedCornerShape(16.dp))
                .testTag("camera_feed_container"),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isDemo && cameraUrl != null) {
                    // Live camera stream from laptop HTTP server
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(cameraUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Laptop Live Camera Feed",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Simulated Surveillance Canvas
                    SurveillanceSimulationCanvas(
                        scanProgress = scanY,
                        motionActive = uiState.motionAlertActive
                    )
                }

                // Surveillance HUD Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Top HUD: Camera ID, REC, Timestamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonAlert.copy(alpha = recAlpha))
                            )
                            Text(
                                text = "LIVE • REC",
                                color = CrimsonAlert,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "CAM 01 // LAPTOP",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = currentTime,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Bottom HUD: FPS & Lock State
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDemo) "FEED: SIMULATED (30 FPS)" else "FEED: LIVE MJPEG",
                            color = Slate400,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        if (uiState.isLocked) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CrimsonAlert.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "LAPTOP LOCKED",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldSafe.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Active",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "GUARD ARMED",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Surveillance Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Take Snapshot Button
            Button(
                onClick = onCaptureSnapshot,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("snapshot_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Navy800,
                    contentColor = CyanAccent
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture Snapshot",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Snapshot", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Lock / Unlock Screen Button
            Button(
                onClick = onLockToggle,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("lock_toggle_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isLocked) CrimsonAlert else Color(0xFF0284C7),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = "Toggle Lock",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isLocked) "Unlock" else "Lock Now",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Refresh Feed Button
            IconButton(
                onClick = onRefreshCamera,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Navy800)
                    .border(1.dp, Navy700, RoundedCornerShape(12.dp))
                    .testTag("refresh_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Feed",
                    tint = Slate400,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Intercom & Voice Warning Fast-Launcher Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("intercom_fast_launcher"),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Navy700)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Laptop Intercom & Mic",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Speak warnings through laptop speakers",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = onNavigateToIntercom,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = Navy900
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Speak", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Security Status Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Navy800.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Navy700)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SURVEILLANCE STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isDemo) "DEMO ACTIVE" else "LIVE LINK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDemo) AmberWarning else EmeraldSafe,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusItem(label = "Webcam", value = "Online (HD)")
                    StatusItem(label = "Motion Sensor", value = "Active")
                    StatusItem(label = "Screen Lock", value = if (uiState.isLocked) "LOCKED" else "Unlocked")
                }
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Slate400)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun SurveillanceSimulationCanvas(
    scanProgress: Float,
    motionActive: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Dark tech grid lines
        val gridSpacing = 40.dp.toPx()
        var x = 0f
        while (x < width) {
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += gridSpacing
        }

        var y = 0f
        while (y < height) {
            drawLine(
                color = Color(0xFF1E293B),
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
            color = Color(0xFF0F2A44),
            radius = maxRadius,
            center = center,
            style = Stroke(width = 1.5f)
        )
        drawCircle(
            color = Color(0xFF0F2A44),
            radius = maxRadius * 0.6f,
            center = center,
            style = Stroke(width = 1.5f)
        )

        // Center reticle
        val reticleSize = 24.dp.toPx()
        drawLine(
            color = if (motionActive) CrimsonAlert else CyanAccent,
            start = Offset(center.x - reticleSize, center.y),
            end = Offset(center.x + reticleSize, center.y),
            strokeWidth = 2f
        )
        drawLine(
            color = if (motionActive) CrimsonAlert else CyanAccent,
            start = Offset(center.x, center.y - reticleSize),
            end = Offset(center.x, center.y + reticleSize),
            strokeWidth = 2f
        )

        // Radar scan line sweep
        val scanYPos = height * scanProgress
        drawLine(
            color = if (motionActive) CrimsonAlert.copy(alpha = 0.5f) else CyanGlow.copy(alpha = 0.4f),
            start = Offset(0f, scanYPos),
            end = Offset(width, scanYPos),
            strokeWidth = 3f
        )

        // Corner viewfinder brackets
        val bracketLen = 30.dp.toPx()
        val p = 16.dp.toPx()
        val bracketColor = if (motionActive) CrimsonAlert else CyanAccent

        // Top Left
        drawLine(bracketColor, Offset(p, p), Offset(p + bracketLen, p), strokeWidth = 2.5f)
        drawLine(bracketColor, Offset(p, p), Offset(p, p + bracketLen), strokeWidth = 2.5f)

        // Top Right
        drawLine(bracketColor, Offset(width - p, p), Offset(width - p - bracketLen, p), strokeWidth = 2.5f)
        drawLine(bracketColor, Offset(width - p, p), Offset(width - p, p + bracketLen), strokeWidth = 2.5f)

        // Bottom Left
        drawLine(bracketColor, Offset(p, height - p), Offset(p + bracketLen, height - p), strokeWidth = 2.5f)
        drawLine(bracketColor, Offset(p, height - p), Offset(p, height - p - bracketLen), strokeWidth = 2.5f)

        // Bottom Right
        drawLine(bracketColor, Offset(width - p, height - p), Offset(width - p - bracketLen, height - p), strokeWidth = 2.5f)
        drawLine(bracketColor, Offset(width - p, height - p), Offset(width - p, height - p - bracketLen), strokeWidth = 2.5f)
    }
}
