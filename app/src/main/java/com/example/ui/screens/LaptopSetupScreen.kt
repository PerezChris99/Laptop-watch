package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import com.example.util.LaptopCompanionScript
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var laptopName by remember { mutableStateOf(config?.laptopName ?: "My Laptop") }
    var ipAddress by remember { mutableStateOf(config?.ipAddress ?: "192.168.1.100") }
    var portText by remember { mutableStateOf((config?.port ?: 5000).toString()) }
    var pin by remember { mutableStateOf(config?.pin ?: "7890") }
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
                "Link & Config" to Icons.Default.Lan,
                "Away & Guard" to Icons.Default.Sensors,
                "PC Scripts" to Icons.Default.Terminal
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
            // TAB 0: LINK & CONFIGURATION
            if (selectedSetupTab == 0) {
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
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Laptop Name
                        OutlinedTextField(
                            value = laptopName,
                            onValueChange = {
                                laptopName = it
                                savedSuccess = false
                            },
                            label = { Text("Laptop Name", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = DeepSlate800),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("laptop_name_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SkyBluePrimary,
                                unfocusedBorderColor = LightBlue100
                            )
                        )

                        // IP Address & Port (Side by Side)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = {
                                    ipAddress = it
                                    savedSuccess = false
                                },
                                label = { Text("Local IP Address", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 12.sp, color = DeepSlate800, fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .weight(2.2f)
                                    .height(50.dp)
                                    .testTag("ip_address_input"),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SkyBluePrimary,
                                    unfocusedBorderColor = LightBlue100
                                )
                            )

                            OutlinedTextField(
                                value = portText,
                                onValueChange = {
                                    portText = it
                                    savedSuccess = false
                                },
                                label = { Text("Port", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 12.sp, color = DeepSlate800, fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("port_input"),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SkyBluePrimary,
                                    unfocusedBorderColor = LightBlue100
                                )
                            )
                        }

                        // Remote Web URL
                        OutlinedTextField(
                            value = remoteWebUrl,
                            onValueChange = {
                                remoteWebUrl = it
                                savedSuccess = false
                            },
                            label = { Text("Remote Web Tunnel (ngrok / Cloudflare)", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = DeepSlate800, fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("remote_url_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SkyBluePrimary,
                                unfocusedBorderColor = LightBlue100
                            )
                        )

                        // Mode Selector Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Routing Mode:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedSlate500
                            )

                            listOf(
                                "LAN" to "LAN Only",
                                "AUTO" to "Auto-Failover"
                            ).forEach { (modeKey, modeTitle) ->
                                val isSelected = connectionMode == modeKey
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) SkyBluePrimary else LightBlue50)
                                        .border(0.8.dp, if (isSelected) SkyBluePrimary else LightBlue100, RoundedCornerShape(6.dp))
                                        .clickable {
                                            haptics.tick()
                                            connectionMode = modeKey
                                            savedSuccess = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = modeTitle,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) WhitePure else DeepSlate800
                                    )
                                }
                            }
                        }

                        // Save Configuration Button (Prominent, cleanly visible above fold)
                        Button(
                            onClick = {
                                haptics.click()
                                val port = portText.toIntOrNull() ?: 5000
                                onSaveConfig(laptopName, ipAddress, port, pin, remoteWebUrl, connectionMode)
                                savedSuccess = true
                                Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("save_config_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (savedSuccess) EmeraldSafe else SkyBluePrimary,
                                contentColor = WhitePure
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (savedSuccess) Icons.Default.CheckCircle else Icons.Default.Save,
                                contentDescription = "Save Settings",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (savedSuccess) "Configuration Saved!" else "Save Link Configuration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // Demo Mode Toggle Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(LightBlue50)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Safe Demo Simulator",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepSlate800
                                )
                                Text(
                                    text = if (uiState.connectionState == ConnectionState.DEMO_MODE) "(ACTIVE)" else "(OFF)",
                                    fontSize = 10.sp,
                                    color = if (uiState.connectionState == ConnectionState.DEMO_MODE) AmberWarning else MutedSlate500,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = uiState.connectionState == ConnectionState.DEMO_MODE,
                                onCheckedChange = {
                                    haptics.tick()
                                    onToggleDemoMode()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = WhitePure,
                                    checkedTrackColor = AmberWarning,
                                    uncheckedThumbColor = WhitePure,
                                    uncheckedTrackColor = LightBlue100
                                ),
                                modifier = Modifier.scale(0.8f)
                            )
                        }
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

            // TAB 2: PC SCRIPTS & LID POLICY
            if (selectedSetupTab == 2) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Windows Companion Script Card
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
                            Text(
                                text = "1. Laptop Companion Setup",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSlate800
                            )
                            Text(
                                text = "Paste in PowerShell on your laptop to run defender-safe daemon:",
                                fontSize = 11.sp,
                                color = MutedSlate500
                            )

                            val psCommand = "irm https://raw.githubusercontent.com/laptopguard/daemon/main/setup.ps1 | iex"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0F172A))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = psCommand,
                                    color = Color(0xFF7DD3FC),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(psCommand))
                                    Toast.makeText(context, "PowerShell command copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy PowerShell Setup Command", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Lid-Close & Sleep Policy Guide Card
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "2. Keep Guarding When Lid Closes",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepSlate800
                                )
                            }
                            Text(
                                text = "Set lid action to 'Do Nothing' so laptop webcam monitors even closed:",
                                fontSize = 11.sp,
                                color = MutedSlate500
                            )

                            val lidCmd = "powercfg /setdcvalueindex SCHEME_CURRENT 4f971e89-eebd-4455-a8de-9e59040e7347 5ca83367-6e45-459f-a27b-476b1d01c936 0"
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(lidCmd))
                                    Toast.makeText(context, "Lid-close command copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepSlate800),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy 1-Click Lid-Close CMD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
