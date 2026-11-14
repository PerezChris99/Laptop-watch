package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.theme.WhiteSmoke
import com.example.ui.theme.DeepSlate800

/** Must be accepted before any monitoring or network service may start.
 * This acknowledgment does not replace permission and bystander consent. */
@Composable
fun FirstRunConsent(onAccept: () -> Unit) {
    var ownershipConfirmed by remember { mutableStateOf(false) }
    var recordingConfirmed by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(WhiteSmoke)
            .verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("LaptopWatch", style = MaterialTheme.typography.headlineMedium, color = DeepSlate800)
        Text("Use your own laptop. Keep recording visible and lawful.",
            style = MaterialTheme.typography.titleMedium, color = DeepSlate800)
        Text(
            "LaptopWatch can access a computer camera, capture photos and video, and issue lock or alarm commands. " +
                "It must never be used to secretly watch people or devices without authorization. " +
                "Camera hardware indicators must remain visible. Video and recordings are sensitive.",
            color = DeepSlate800
        )
        Text(
            "The app cannot guarantee location, video or communication while a laptop is asleep, offline, " +
                "powered down, or lacks a genuine positioning source. It cannot prevent theft.",
            color = DeepSlate800
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ownershipConfirmed, onCheckedChange = { ownershipConfirmed = it })
            Text("I own or am authorized to control the laptop being monitored.")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = recordingConfirmed, onCheckedChange = { recordingConfirmed = it })
            Text("I will follow recording and privacy laws, including required consent and notices.")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAccept,
            enabled = ownershipConfirmed && recordingConfirmed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I agree — continue to pairing")
        }
        Text(
            "You can disarm at any time. Do not activate remote monitoring before securing the laptop.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray
        )
    }
}
