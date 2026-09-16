package com.example.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LaptopConfigEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlue50
import com.example.ui.theme.MutedSlate500
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WhitePure
import com.example.viewmodel.ConnectionState
import com.example.viewmodel.MonitorUiState

@Composable
fun TopStatusHeader(
    config: LaptopConfigEntity?,
    uiState: MonitorUiState,
    onLockClick: () -> Unit,
    onAlarmClick: () -> Unit,
    onMotionAlertClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_status_header"),
        color = WhitePure,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = LightBlue100)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Main Top Bar Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Laptop info and connection indicator
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Status dot
                        val dotColor = when (uiState.connectionState) {
                            ConnectionState.CONNECTED -> EmeraldSafe
                            ConnectionState.DEMO_MODE -> AmberWarning
                            ConnectionState.CONNECTING -> SkyBluePrimary
                            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> CrimsonAlert
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .then(
                                    if (uiState.connectionState == ConnectionState.CONNECTING)
                                        Modifier.alpha(pulseAlpha) else Modifier
                                )
                        )

                        Text(
                            text = config?.laptopName ?: "Laptop Guard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate800,
                            maxLines = 1
                        )

                        // Transport Badge (LAN or Web)
                        val transportIcon = if (uiState.activeTransport == "WEB") Icons.Default.CloudDone else Icons.Default.Lan
                        val transportLabel = if (uiState.activeTransport == "WEB") "Web" else "LAN"
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LightBlue50)
                                .border(0.8.dp, LightBlue100, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = transportIcon,
                                contentDescription = transportLabel,
                                tint = SkyBluePrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = transportLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SkyBlueDark
                            )
                        }

                        // Network Latency Badge
                        uiState.networkLatencyMs?.let { latency ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (latency < 60) EmeraldSafe else AmberWarning)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${latency}ms",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepSlate800
                                )
                            }
                        }

                        // Away Mode Badge if active
                        if (uiState.isAwayMode) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFEF3C7))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "AWAY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val statusText = when (uiState.connectionState) {
                            ConnectionState.CONNECTED -> {
                                if (uiState.activeTransport == "WEB") "Connected via Web Tunnel"
                                else "Local LAN (${config?.ipAddress})"
                            }
                            ConnectionState.DEMO_MODE -> "Safe Demo Simulator"
                            ConnectionState.CONNECTING -> "Resolving network route..."
                            ConnectionState.DISCONNECTED -> "Offline"
                            ConnectionState.ERROR -> "Link Offline"
                        }
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            color = MutedSlate500,
                            fontFamily = FontFamily.SansSerif
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "Battery",
                                tint = EmeraldSafe,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = uiState.batteryLevel,
                                fontSize = 11.sp,
                                color = MutedSlate500,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Quick Action Buttons (Alarm & Lock)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Deterrent Alarm Quick Trigger
                    IconButton(
                        onClick = onAlarmClick,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFF1F2))
                            .border(1.dp, Color(0xFFFECDD3), CircleShape)
                            .testTag("quick_alarm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Trigger Siren Alarm",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Main Lockdown Button
                    val isLocked = uiState.isLocked
                    val lockBtnBg by animateColorAsState(
                        targetValue = if (isLocked) CrimsonAlert else SkyBluePrimary,
                        label = "lockBg"
                    )

                    Button(
                        onClick = onLockClick,
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("quick_lock_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = lockBtnBg,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) "Locked" else "Lock Laptop",
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLocked) "LOCKED" else "LOCK",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Motion Alert Bar if active
            AnimatedVisibility(visible = uiState.motionAlertActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF1F2))
                        .border(1.dp, Color(0xFFFDA4AF), RoundedCornerShape(10.dp))
                        .clickable { onMotionAlertClick() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Motion detected in front of laptop!",
                            color = CrimsonAlert,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "VIEW ➔",
                        color = CrimsonAlert,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

