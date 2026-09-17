package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlue50
import com.example.ui.theme.LightBlueSoft
import com.example.ui.theme.MutedSlate500
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WhitePure
import com.example.ui.theme.WhiteSmoke
import com.example.util.rememberAppHaptics
import com.example.viewmodel.MonitorUiState

@Composable
fun VoiceIntercomScreen(
    uiState: MonitorUiState,
    onStartRecording: () -> Unit,
    onStopAndSendRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendTTS: (String) -> Unit,
    onTriggerAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var customMessage by remember { mutableStateOf("") }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            onStartRecording()
        }
    }

    // Animation for active microphone pulse
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    val scrollState = rememberScrollState()
    var selectedIntercomTab by remember { mutableStateOf(0) }
    val haptics = rememberAppHaptics()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WhiteSmoke)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LightBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Voice Intercom",
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "Laptop Intercom & Mic",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepSlate800
                )
                Text(
                    text = "Broadcast voice warnings directly to laptop speakers",
                    fontSize = 12.sp,
                    color = MutedSlate500
                )
            }
        }

        // Clean Segmented Tabs to Declutter Screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LightBlue50)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                "Live Mic" to Icons.Default.Mic,
                "Presets" to Icons.Default.Campaign,
                "Custom Text" to Icons.Default.ChatBubbleOutline
            )
            tabs.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedIntercomTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) WhitePure else Color.Transparent)
                        .clickable {
                            haptics.tick()
                            selectedIntercomTab = index
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) SkyBluePrimary else MutedSlate500,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) DeepSlate800 else MutedSlate500
                        )
                    }
                }
            }
        }

        // TAB 0: PUSH-TO-TALK HERO CARD
        if (selectedIntercomTab == 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("push_to_talk_card"),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (uiState.isRecordingVoice) CrimsonAlert else LightBlue100
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (uiState.isRecordingVoice) "TRANSMITTING LIVE VOICE..." else "PUSH TO TALK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isRecordingVoice) CrimsonAlert else SkyBlueDark,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Big Mic Button with Tap-and-Hold
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .scale(if (uiState.isRecordingVoice) micScale else 1f)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isRecordingVoice) CrimsonAlert else LightBlueSoft
                        )
                        .border(
                            2.dp,
                            if (uiState.isRecordingVoice) CrimsonAlert else SkyBluePrimary,
                            CircleShape
                        )
                        .pointerInput(hasMicPermission) {
                            detectTapGestures(
                                onPress = {
                                    if (!hasMicPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        haptics.click()
                                        onStartRecording()
                                        tryAwaitRelease()
                                        haptics.tick()
                                        onStopAndSendRecording()
                                    }
                                }
                            )
                        }
                        .testTag("push_to_talk_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isRecordingVoice) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Hold to Speak",
                        tint = if (uiState.isRecordingVoice) Color.White else SkyBluePrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.isRecordingVoice) {
                    Text(
                        text = "Recording: ${uiState.recordingDurationSec}s • Release to broadcast",
                        color = CrimsonAlert,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Press & hold to speak into your phone's microphone",
                        color = MutedSlate500,
                        fontSize = 12.sp
                    )
                }
            }
        }
        }

        // TAB 1: QUICK WARNING PRESETS (TTS)
        if (selectedIntercomTab == 1) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "INSTANT VOICE WARNING PRESETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SkyBlueDark,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val warningPresets = listOf(
                "🚨 Step away from this computer! You are being recorded!" to CrimsonAlert,
                "⚠️ Unauthorized user detected! Locking laptop now." to AmberWarning,
                "📢 Security Alert: Owner has been notified of your presence." to SkyBluePrimary,
                "🛑 Do not touch this keyboard! Security alarm armed." to Color(0xFFE11D48)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                warningPresets.forEachIndexed { index, (preset, color) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.click()
                                onSendTTS(preset)
                            }
                            .testTag("warning_preset_$index"),
                        colors = CardDefaults.cardColors(containerColor = WhitePure),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Play Warning",
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = preset,
                                    color = DeepSlate800,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = SkyBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        }

        // TAB 2: CUSTOM TEXT-TO-SPEECH ANNOUNCEMENT
        if (selectedIntercomTab == 2) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CUSTOM VOICE ANNOUNCEMENT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SkyBlueDark,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WhitePure),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        label = { Text("Type warning to speak out loud on laptop") },
                        placeholder = { Text("e.g. Please leave my desk immediately!") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_warning_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DeepSlate800,
                            unfocusedTextColor = DeepSlate800,
                            focusedBorderColor = SkyBluePrimary,
                            unfocusedBorderColor = LightBlue100,
                            focusedLabelColor = SkyBluePrimary,
                            unfocusedLabelColor = MutedSlate500,
                            focusedContainerColor = WhitePure,
                            unfocusedContainerColor = WhitePure
                        ),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (customMessage.isNotBlank()) {
                                haptics.click()
                                onSendTTS(customMessage)
                                customMessage = ""
                            }
                        },
                        enabled = customMessage.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("broadcast_custom_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyBluePrimary,
                            contentColor = Color.White,
                            disabledContainerColor = LightBlue100,
                            disabledContentColor = MutedSlate500
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Broadcast",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Broadcast Message to Laptop", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        }

        // DETERRENT HIGH-DECIBEL SIREN ALARM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("deterrent_alarm_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECDD3))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Siren",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Anti-Theft Siren Alarm",
                            color = DeepSlate800,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Blasts high-pitch alarm deterrent on laptop",
                            color = MutedSlate500,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        haptics.alarmWarning()
                        onTriggerAlarm()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonAlert,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("trigger_siren_button")
                ) {
                    Text("SOUND ALARM", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
