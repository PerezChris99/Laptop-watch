package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.Navy700
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
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
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy900)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CyanAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Voice Intercom",
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "LAPTOP INTERCOM & MIC",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Broadcast voice warnings directly to laptop speakers",
                    fontSize = 12.sp,
                    color = Slate400
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // PUSH-TO-TALK HERO SECTION
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("push_to_talk_card"),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (uiState.isRecordingVoice) CrimsonAlert else Navy700
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (uiState.isRecordingVoice) "TRANSMITTING VOICE..." else "PUSH TO TALK",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isRecordingVoice) CrimsonAlert else CyanAccent,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Big Mic Button with Tap-and-Hold / Toggle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .scale(if (uiState.isRecordingVoice) micScale else 1f)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isRecordingVoice) CrimsonAlert else CyanAccent
                        )
                        .pointerInput(hasMicPermission) {
                            detectTapGestures(
                                onPress = {
                                    if (!hasMicPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        onStartRecording()
                                        tryAwaitRelease()
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
                        tint = if (uiState.isRecordingVoice) Color.White else Navy900,
                        modifier = Modifier.size(52.dp)
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
                        text = "Press & hold to speak into Tecno phone mic",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // QUICK WARNING PRESETS (TTS)
        Text(
            text = "INSTANT VOICE WARNING PRESETS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Slate400,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        val warningPresets = listOf(
            "🚨 Step away from this computer! You are being recorded!" to CrimsonAlert,
            "⚠️ Unauthorized user detected! Locking laptop now." to AmberWarning,
            "📢 Security Alert: Owner has been notified of your presence." to CyanAccent,
            "🛑 Do not touch this keyboard! Security alarm armed." to Color(0xFFF43F5E)
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
                        .clickable { onSendTTS(preset) }
                        .testTag("warning_preset_$index"),
                    colors = CardDefaults.cardColors(containerColor = Navy800),
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
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // CUSTOM TEXT-TO-SPEECH ANNOUNCEMENT
        Text(
            text = "CUSTOM VOICE ANNOUNCEMENT",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Slate400,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Navy700)
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
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = Slate600,
                        focusedLabelColor = CyanAccent,
                        unfocusedLabelColor = Slate400
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (customMessage.isNotBlank()) {
                            onSendTTS(customMessage)
                            customMessage = ""
                        }
                    },
                    enabled = customMessage.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("broadcast_custom_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = Navy900
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Broadcast",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Broadcast Message to Laptop", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // DETERRENT HIGH-DECIBEL SIREN ALARM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("deterrent_alarm_card"),
            colors = CardDefaults.cardColors(containerColor = CrimsonAlert.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f))
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
                            .background(CrimsonAlert.copy(alpha = 0.2f)),
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
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Plays high-pitch alarm deterrent on laptop",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = onTriggerAlarm,
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
    }
}
