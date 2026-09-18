package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LaptopConfigEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlue50
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WhitePure
import com.example.util.rememberAppHaptics
import com.example.viewmodel.ConnectionState
import com.example.viewmodel.MonitorUiState

/**
 * Ultra-compact, single-row TopStatusHeader reduced by more than half in height.
 * Contains strictly: Status Dot, Laptop Title, Battery, and Lock Button.
 * Removes vertical latency numbers (28ms) and extra badges to maximize screen space.
 */
@Composable
fun TopStatusHeader(
    config: LaptopConfigEntity?,
    uiState: MonitorUiState,
    onLockClick: () -> Unit,
    onAlarmClick: () -> Unit = {},
    onMotionAlertClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val haptics = rememberAppHaptics()
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
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Group: Status Indicator Dot + Title + Battery Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Connection status dot
                    val dotColor = when (uiState.connectionState) {
                        ConnectionState.CONNECTED -> EmeraldSafe
                        ConnectionState.DEMO_MODE -> AmberWarning
                        ConnectionState.CONNECTING -> SkyBluePrimary
                        ConnectionState.DISCONNECTED, ConnectionState.ERROR -> CrimsonAlert
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .then(
                                if (uiState.connectionState == ConnectionState.CONNECTING)
                                    Modifier.alpha(pulseAlpha) else Modifier
                            )
                    )

                    // Laptop Title
                    Text(
                        text = config?.laptopName ?: "Laptop Guard",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSlate800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Battery Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(LightBlue50)
                            .border(0.6.dp, LightBlue100, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery",
                            tint = EmeraldSafe,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = uiState.batteryLevel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate800
                        )
                    }
                }

                // Right Group: Lock Button ONLY
                val isLocked = uiState.isLocked
                val lockBtnBg by animateColorAsState(
                    targetValue = if (isLocked) CrimsonAlert else SkyBluePrimary,
                    label = "lockBg"
                )

                Button(
                    onClick = {
                        haptics.lockToggle(isLocked)
                        onLockClick()
                    },
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("quick_lock_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = lockBtnBg,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Locked" else "Lock Laptop",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isLocked) "LOCKED" else "LOCK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.8.dp,
                color = LightBlue100
            )
        }
    }
}
