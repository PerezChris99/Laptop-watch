package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LaptopConfigEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlue50
import com.example.ui.theme.LightBlueSoft
import com.example.ui.theme.MutedSlate500
import com.example.ui.theme.NightSecurityThemeState
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WhitePure
import com.example.ui.theme.WhiteSmoke
import com.example.util.rememberAppHaptics
import com.example.viewmodel.ConnectionState
import com.example.viewmodel.MonitorUiState

/**
 * Screen for Laptop Link & Security Setup.
 * Engineered to perfectly fit the viewport without vertical scrolling or bottom-bar cutoff.
 */
@Composable
fun LaptopSetupScreen(
    config: LaptopConfigEntity?,
    uiState: MonitorUiState,
    onSaveConfig: (name: String, ip: String, port: Int, pin: String, webUrl: String, mode: String) -> Unit,
    onTestConnection: () -> Unit,
    onToggleDemoMode: () -> Unit,
    onToggleAwayMode: () -> Unit = {},
    onSetAwaySensitivity: (String) -> Unit = {},
    onToggleBackgroundService: () -> Unit = {},
    onScanPairingQr: (String) -> Unit = {},
    onVerifySecurePairing: () -> Unit = {},
    onForgetSecurePairing: () -> Unit = {},
    onSetSecureCameraArmed: (Boolean) -> Unit = {},
    onGetSecureLastSeen: () -> Unit = {},
    onRediscoverSecureLaptop: () -> Unit = {},
    onConfigureRemoteRelay: (String) -> Unit = {},
    onCopyRemoteEnrollment: () -> String = { "" },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(context, if (granted)
            "Person incident notifications enabled on this phone"
            else "Notifications disabled; foreground events remain available in logs",
            Toast.LENGTH_LONG).show()
    }

    var laptopName by remember { mutableStateOf(config?.laptopName ?: "My Laptop") }
    var ipAddress by remember { mutableStateOf(config?.ipAddress ?: "192.168.1.100") }
    var portText by remember { mutableStateOf((config?.port ?: 5000).toString()) }
    var pin by remember { mutableStateOf(config?.pin ?: "") }
    var remoteWebUrl by remember { mutableStateOf(config?.remoteWebUrl ?: "") }
    var connectionMode by remember { mutableStateOf(config?.connectionMode ?: "AUTO") }
    var savedSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(config) {
        if (config != null) {
            laptopName = config.laptopName
            ipAddress = config.ipAddress
            portText = config.port.toString()
            pin = config.pin
            remoteWebUrl = config.remoteWebUrl
            connectionMode = config.connectionMode
        }
    }

    var selectedSetupTab by remember { mutableStateOf(0) }
    var relayDraft by remember { mutableStateOf(uiState.secureRelayUrl) }
    val haptics = rememberAppHaptics()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WhiteSmoke)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top Header Bar: Title + Status + Test Ping
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(LightBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Link & Security Setup",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSlate800
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isConnected = uiState.connectionState == ConnectionState.CONNECTED
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) EmeraldSafe else CrimsonAlert)
                        )
                        Text(
                            text = if (isConnected) "Connected (${uiState.activeTransport})" else "Offline",
                            fontSize = 10.sp,
                            color = if (isConnected) EmeraldSafe else MutedSlate500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Test Ping Button
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onTestConnection()
                },
                enabled = uiState.connectionState != ConnectionState.CONNECTING,
                modifier = Modifier
                    .height(30.dp)
                    .testTag("test_connection_button"),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBluePrimary)
            ) {
                if (uiState.connectionState == ConnectionState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = SkyBluePrimary,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Test Ping",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ping", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SkyBluePrimary)
                }
            }
        }

        // Segmented Tabs: Connection, Away & Service, PC Scripts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(LightBlue50)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val tabs = listOf(
                "Secure Pairing" to Icons.Default.QrCodeScanner,
                "Away & Guard" to Icons.Default.Sensors,
                "Install info" to Icons.Default.Terminal
            )
            tabs.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedSetupTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) WhitePure else Color.Transparent)
                        .clickable {
                            haptics.tick()
                            selectedSetupTab = index
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
                            tint = if (isSelected) SkyBluePrimary else MutedSlate500,
                            modifier = Modifier.size(13.dp)
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

        // Main Tab Content Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // TAB 3: PINNED QR PAIRING — primary and secure setup path.
            if (selectedSetupTab == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WhitePure),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Secure laptop pairing", fontSize = 18.sp,
                            color = DeepSlate800, fontWeight = FontWeight.Bold)
                        Text(
                            "1. On the desktop companion choose Pair Android and show its QR. " +
                                "2. Scan here; approve this phone on the laptop. " +
                                "3. Tap Verify. Each phone has an independent, encrypted signing key.",
                            fontSize = 12.sp, color = MutedSlate500
                        )
                        Text(
                            "Phone status: " + uiState.securePairingStatus,
                            color = if (uiState.securePairingStatus == "PAIRED") EmeraldSafe else AmberWarning,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                GmsBarcodeScanning.getClient(context).startScan()
                                    .addOnSuccessListener { barcode ->
                                        val raw = barcode.rawValue
                                        if (raw != null && raw.startsWith("laptopwatch://pair?")) {
                                            onScanPairingQr(raw)
                                        } else {
                                            Toast.makeText(context, "Not a LaptopWatch QR", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .addOnFailureListener { error ->
                                        Toast.makeText(context,
                                            error.message ?: "QR scanner unavailable",
                                            Toast.LENGTH_LONG).show()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("scan_laptop_qr_button"),
                            enabled = !uiState.isSendingAction
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Laptop QR", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = onVerifySecurePairing,
                            modifier = Modifier.fillMaxWidth()
                                .testTag("verify_secure_pairing_button"),
                            enabled = uiState.securePairingStatus != "NOT_PAIRED"
                        ) { Text("Verify laptop approval", fontSize = 13.sp) }
                        if (uiState.securePairingStatus == "PAIRED") {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context,
                                    Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED) {
                                OutlinedButton(
                                    onClick = {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS)
                                    }, modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Allow verified person alerts on this phone",
                                        fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = { onSetSecureCameraArmed(!uiState.secureArmed) },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("toggle_secure_camera_button")
                            ) {
                                Text(if (uiState.secureArmed) "Disarm laptop camera"
                                    else "Arm laptop camera (visible capture)", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = onRediscoverSecureLaptop,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Find paired laptop on this Wi-Fi", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onGetSecureLastSeen,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retrieve last verified laptop location", fontSize = 12.sp)
                            }
                            Text(
                                "Optional stolen-device last seen: only works after you deploy and provision a " +
                                    "separate signed HTTPS location relay. The laptop must have a real location source.",
                                color = MutedSlate500, fontSize = 11.sp
                            )
                            OutlinedTextField(
                                value = relayDraft,
                                onValueChange = { relayDraft = it },
                                label = { Text("Optional HTTPS location relay", fontSize = 12.sp) },
                                placeholder = { Text("https://relay.yourdomain.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = { onConfigureRemoteRelay(relayDraft) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save location relay (opt in)", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(onCopyRemoteEnrollment()))
                                    Toast.makeText(context, "Public pairing key copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Copy phone public key for relay enrollment", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = onForgetSecurePairing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Forget this phone pairing", fontSize = 12.sp)
                            }
                        }
                        Text(
                            "No stealth capture. Camera activity is visible. " +
                                "The laptop must be awake; never assume GPS without a real receiver. " +
                                "The old HTTP/PIN installer is not safe for production.",
                            color = MutedSlate500, fontSize = 11.sp
                        )
                    }
                }
            }

            // TAB 1: AWAY & SERVICE
            if (selectedSetupTab == 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // System-Wide Night Surveillance Dark Mode Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("night_mode_card"),
                        colors = CardDefaults.cardColors(containerColor = WhitePure),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(LightBlueSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = "Night Surveillance",
                                        tint = SkyBluePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Night Surveillance Theme",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepSlate800
                                    )
                                    Text(
                                        text = if (NightSecurityThemeState.isDarkMode)
                                            "ACTIVE • Obsidian dark theme saves battery & eliminates nighttime eye glare"
                                        else "INACTIVE • Standard day station theme",
                                        fontSize = 11.sp,
                                        color = if (NightSecurityThemeState.isDarkMode) EmeraldSafe else MutedSlate500,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Switch(
                                checked = NightSecurityThemeState.isDarkMode,
                                onCheckedChange = { NightSecurityThemeState.isDarkMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = WhitePure,
                                    checkedTrackColor = SkyBluePrimary
                                ),
                                modifier = Modifier.testTag("dark_mode_theme_switch")
                            )
                        }
                    }

                    // Away Vigilance Mode Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WhitePure),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Away Vigilance Mode",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepSlate800
                                    )
                                    Text(
                                        text = if (uiState.isAwayMode) "ACTIVE • Higher trigger sensitivity" else "INACTIVE",
                                        fontSize = 11.sp,
                                        color = if (uiState.isAwayMode) CrimsonAlert else MutedSlate500,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Switch(
                                    checked = uiState.isAwayMode,
                                    onCheckedChange = { onToggleAwayMode() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = WhitePure,
                                        checkedTrackColor = CrimsonAlert
                                    ),
                                    modifier = Modifier.testTag("away_mode_switch")
                                )
                            }

                            // Sensitivity Chips
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
                                listOf("LOW", "MEDIUM", "HIGH").forEach { level ->
                                    val isSelected = uiState.awaySensitivity == level
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) SkyBluePrimary else LightBlue50)
                                            .clickable { onSetAwaySensitivity(level) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = level,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) WhitePure else DeepSlate800
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Background Vigilance Sentinel Service Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WhitePure),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.isBackgroundServiceActive) EmeraldSafe.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = if (uiState.isBackgroundServiceActive) EmeraldSafe else AmberWarning,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Background Sentinel Service",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DeepSlate800
                                        )
                                        Text(
                                            text = if (uiState.isBackgroundServiceActive) "RUNNING • WakeLock Armed" else "STOPPED",
                                            fontSize = 10.sp,
                                            color = if (uiState.isBackgroundServiceActive) EmeraldSafe else AmberWarning,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Switch(
                                    checked = uiState.isBackgroundServiceActive,
                                    onCheckedChange = { onToggleBackgroundService() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = WhitePure,
                                        checkedTrackColor = SkyBluePrimary
                                    ),
                                    modifier = Modifier.testTag("bg_service_switch")
                                )
                            }
                            Text(
                                text = "Guards laptop even when phone screen is off or other apps are active.",
                                fontSize = 11.sp,
                                color = MutedSlate500
                            )
                        }
                    }
                }
            }

            // TAB 2: Desktop installation and recovery — no unsafe scripts.
            if (selectedSetupTab == 2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WhitePure),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Install the trusted desktop companion",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = DeepSlate800)
                        Text(
                            "Install the signed LaptopWatch desktop application on a device you own. " +
                                "Start it, generate a two-minute QR invitation and approve your phone locally. " +
                                "Legacy PowerShell one-line installers and PIN/HTTP daemons are removed from " +
                                "the supported setup flow because they cannot provide verified trust.",
                            fontSize = 12.sp, color = DeepSlate800
                        )
                        Text(
                            "Do not disable laptop lid safety or force the camera to run while closed: " +
                                "the webcam is usually obstructed and running hardware in a bag can overheat.",
                            fontSize = 12.sp, color = MutedSlate500
                        )
                        Text(
                            "For theft recovery, enable Windows Find My Device or Apple Find My " +
                                "in your OS account before loss. LaptopWatch cannot track a powered-off machine.",
                            fontSize = 12.sp, color = MutedSlate500
                        )
                    }
                }
            }
        }
    }
}
