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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.Navy700
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.Slate400
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
        color = Navy800,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                            ConnectionState.CONNECTING -> Color(0xFF38BDF8)
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
                            color = Color.White,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val statusText = when (uiState.connectionState) {
                            ConnectionState.CONNECTED -> "Online (${config?.ipAddress})"
                            ConnectionState.DEMO_MODE -> "Demo Mode (Simulated)"
                            ConnectionState.CONNECTING -> "Connecting..."
                            ConnectionState.DISCONNECTED -> "Offline"
                            ConnectionState.ERROR -> "Connection Error"
                        }
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            color = Slate400,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "Battery",
                                tint = EmeraldSafe,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = uiState.batteryLevel,
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }
                }

                // Quick Action Buttons (Lock & Alarm)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Deterrent Alarm Quick Trigger
                    IconButton(
                        onClick = onAlarmClick,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Navy700)
                            .border(1.dp, CrimsonAlert.copy(alpha = 0.5f), CircleShape)
                            .testTag("quick_alarm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Trigger Siren Alarm",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Main Lockdown Button
                    val isLocked = uiState.isLocked
                    val lockBtnBg by animateColorAsState(
                        targetValue = if (isLocked) CrimsonAlert else Color(0xFF0284C7),
                        label = "lockBg"
                    )

                    Button(
                        onClick = onLockClick,
                        modifier = Modifier
                            .height(44.dp)
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
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLocked) "LOCKED" else "LOCK",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(CrimsonAlert.copy(alpha = 0.2f))
                        .border(1.dp, CrimsonAlert, RoundedCornerShape(8.dp))
                        .clickable { onMotionAlertClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                            text = "⚠️ Motion detected in front of laptop!",
                            color = CrimsonAlert,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "VIEW ➔",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
