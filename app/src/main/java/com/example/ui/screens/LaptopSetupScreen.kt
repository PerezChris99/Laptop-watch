package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.data.LaptopConfigEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlue50
import com.example.ui.theme.LightBlueSoft
import com.example.ui.theme.MutedSlate500
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.WhitePure
import com.example.ui.theme.WhiteSmoke
import com.example.util.LaptopCompanionScript
import com.example.viewmodel.ConnectionState
import com.example.viewmodel.MonitorUiState

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
    var laptopName by remember { mutableStateOf(config?.laptopName ?: "My Laptop") }
    var ipAddress by remember { mutableStateOf(config?.ipAddress ?: "192.168.1.100") }
    var portText by remember { mutableStateOf((config?.port ?: 5000).toString()) }
    var pin by remember { mutableStateOf(config?.pin ?: "7890") }
    var remoteWebUrl by remember { mutableStateOf(config?.remoteWebUrl ?: "") }
    var connectionMode by remember { mutableStateOf(config?.connectionMode ?: "AUTO") }
    var showDaemonHelp by remember { mutableStateOf(false) }

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

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WhiteSmoke)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Page Title & Subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LightBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Connection Settings",
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "Link & Security Setup",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepSlate800
                )
                Text(
                    text = "Configure dual-route access and background surveillance",
                    fontSize = 12.sp,
                    color = MutedSlate500
                )
            }
        }

        // Section 1: Active Connection Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connection_status_card"),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NETWORK LINK STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkyBlueDark,
                        letterSpacing = 1.sp
                    )

                    // Active Route Badge
                    val routeLabel = if (uiState.activeTransport == "WEB") "Web Cloud Tunnel" else "Local LAN"
                    val routeIcon = if (uiState.activeTransport == "WEB") Icons.Default.CloudDone else Icons.Default.Lan
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LightBlue50)
                            .border(1.dp, LightBlue100, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = routeIcon,
                            contentDescription = null,
                            tint = SkyBluePrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = routeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SkyBlueDark
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val statusDotColor = when (uiState.connectionState) {
                        ConnectionState.CONNECTED -> EmeraldSafe
                        ConnectionState.DEMO_MODE -> AmberWarning
                        ConnectionState.CONNECTING -> SkyBluePrimary
                        ConnectionState.DISCONNECTED, ConnectionState.ERROR -> CrimsonAlert
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Text(
                        text = uiState.statusMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = DeepSlate800
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTestConnection,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("test_connection_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyBluePrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (uiState.connectionState == ConnectionState.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Detect Link")
                        }
                    }

                    OutlinedButton(
                        onClick = onToggleDemoMode,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("toggle_demo_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
                    ) {
                        Text(
                            text = if (config?.isDemoMode == true) "Exit Demo" else "Demo Mode",
                            fontSize = 13.sp,
                            color = SkyBlueDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section 2: Auto-Detection & Dual Routing Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "CONNECTION ROUTING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SkyBlueDark,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Select how the app connects to your laptop. In Auto mode, it uses LAN when on the same Wi-Fi and automatically switches to the Web Tunnel when you are away.",
                    fontSize = 12.sp,
                    color = MutedSlate500,
                    lineHeight = 17.sp
                )

                // Mode Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("AUTO" to "Auto Detect", "LAN" to "Same Network", "WEB" to "Web Tunnel").forEach { (modeKey, label) ->
                        val isSelected = connectionMode == modeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SkyBluePrimary else LightBlueSoft)
                                .border(
                                    1.dp,
                                    if (isSelected) SkyBluePrimary else LightBlue100,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { connectionMode = modeKey }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DeepSlate800
                            )
                        }
                    }
                }

                HorizontalDivider(color = LightBlue100)

                // Laptop Device Name
                OutlinedTextField(
                    value = laptopName,
                    onValueChange = { laptopName = it },
                    label = { Text("Laptop Identifier Name") },
                    leadingIcon = {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = SkyBluePrimary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_laptop_name"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyBluePrimary,
                        unfocusedBorderColor = LightBlue100,
                        focusedLabelColor = SkyBluePrimary,
                        unfocusedLabelColor = MutedSlate500
                    ),
                    singleLine = true
                )

                // Local LAN IP & Port
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("Local IP (Same Wi-Fi)") },
                        leadingIcon = {
                            Icon(Icons.Default.Lan, contentDescription = null, tint = SkyBluePrimary)
                        },
                        modifier = Modifier
                            .weight(2f)
                            .testTag("input_ip_address"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyBluePrimary,
                            unfocusedBorderColor = LightBlue100,
                            focusedLabelColor = SkyBluePrimary,
                            unfocusedLabelColor = MutedSlate500
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_port"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyBluePrimary,
                            unfocusedBorderColor = LightBlue100,
                            focusedLabelColor = SkyBluePrimary,
                            unfocusedLabelColor = MutedSlate500
                        ),
                        singleLine = true
                    )
                }

                // Remote Web Tunnel URL (For access away from home)
                OutlinedTextField(
                    value = remoteWebUrl,
                    onValueChange = { remoteWebUrl = it },
                    label = { Text("Remote Web URL (Cloudflare / ngrok)") },
                    placeholder = { Text("https://xxx.trycloudflare.com") },
                    leadingIcon = {
                        Icon(Icons.Default.Public, contentDescription = null, tint = SkyBluePrimary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_web_url"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyBluePrimary,
                        unfocusedBorderColor = LightBlue100,
                        focusedLabelColor = SkyBluePrimary,
                        unfocusedLabelColor = MutedSlate500
                    ),
                    singleLine = true
                )

                // Security PIN
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Security PIN (Must match laptop script)") },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = SkyBluePrimary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_pin"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyBluePrimary,
                        unfocusedBorderColor = LightBlue100,
                        focusedLabelColor = SkyBluePrimary,
                        unfocusedLabelColor = MutedSlate500
                    ),
                    singleLine = true
                )

                // Save button
                Button(
                    onClick = {
                        val parsedPort = portText.toIntOrNull() ?: 5000
                        onSaveConfig(laptopName, ipAddress, parsedPort, pin, remoteWebUrl, connectionMode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("save_config_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkyBluePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Connection Settings", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Section 3: Away Mode & Sensitivity Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                tint = SkyBluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "AWAY MODE RADAR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SkyBlueDark,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Heightened vigilance when you step away from the laptop",
                            fontSize = 12.sp,
                            color = MutedSlate500
                        )
                    }

                    Switch(
                        checked = uiState.isAwayMode,
                        onCheckedChange = { onToggleAwayMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SkyBluePrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = LightBlue100
                        ),
                        modifier = Modifier.testTag("away_mode_switch")
                    )
                }

                if (uiState.isAwayMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🏃 Away Mode is ACTIVE: Instant auto-lock is enforced upon any movement detected in front of your laptop.",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                HorizontalDivider(color = LightBlue100)

                Text(
                    text = "Motion Sensitivity when Away",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepSlate800
                )

                // Sensitivity options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val sensOptions = listOf(
                        "HIGH" to "High (5% change)",
                        "ULTRA" to "Ultra (2% micro-motion)"
                    )
                    sensOptions.forEach { (sensKey, sensDesc) ->
                        val isCurrent = uiState.awaySensitivity == sensKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) SkyBlueSoft else WhitePure)
                                .border(
                                    1.dp,
                                    if (isCurrent) SkyBluePrimary else LightBlue100,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSetAwaySensitivity(sensKey) }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = sensKey,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) SkyBlueDark else DeepSlate800
                                )
                                Text(
                                    text = sensDesc,
                                    fontSize = 10.sp,
                                    color = MutedSlate500
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Windows Defender-Safe Laptop Companion & Installer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldSafe,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "WINDOWS DEFENDER SAFE INSTALLER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SkyBlueDark,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFECFDF5))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✓ 100% DEFENDER CLEAN",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSafe
                        )
                    }
                }

                Text(
                    text = "Runs directly via the official Microsoft Python interpreter using native Win32 APIs — NO compiled .exe files, NO PowerShell shellouts, zero false-positive warnings.",
                    fontSize = 12.sp,
                    color = MutedSlate500,
                    lineHeight = 17.sp
                )

                // 1-Click Installer Batch Button
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("install_windows_guard.bat", LaptopCompanionScript.INSTALL_WINDOWS_BAT)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied install_windows_guard.bat to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("copy_installer_bat_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkyBluePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy 1-Click Installer (install_guard.bat)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Copy Python Script Button
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Laptop Companion Script", LaptopCompanionScript.PYTHON_SCRIPT)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied laptop_guard.py script to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("copy_script_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LightBlue100)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = SkyBlueDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Python Daemon (laptop_guard.py)", fontSize = 12.sp, color = SkyBlueDark, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDaemonHelp = !showDaemonHelp }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installation instructions & macOS / Linux commands",
                        fontSize = 12.sp,
                        color = SkyBluePrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (showDaemonHelp) "Hide ▲" else "Show ▼",
                        fontSize = 12.sp,
                        color = SkyBluePrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                AnimatedVisibility(visible = showDaemonHelp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LightBlue50)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "1. Windows Quick Setup (Zero Flag):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate800
                        )
                        Text(
                            text = "• Save 'laptop_guard.py' and 'install_guard.bat' in any folder on your laptop.\n• Double-click 'install_guard.bat'.\n• It automatically verifies Python (or installs it cleanly via Microsoft winget), installs flask & opencv, and adds a silent background launcher to your Windows Startup folder.\n• The guard runs invisibly in the background on every login!",
                            fontSize = 11.sp,
                            color = DeepSlate800,
                            lineHeight = 16.sp
                        )

                        Text(
                            text = "2. macOS & Linux Background Daemon:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate800
                        )
                        Text(
                            text = "pip install flask opencv-python pyttsx3 psutil\nnohup python3 laptop_guard.py > /dev/null 2>&1 &\n(Runs in the background even after terminal closes).",
                            fontSize = 11.sp,
                            color = DeepSlate800,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )

                        Text(
                            text = "3. Connect over the Web (Cloudflare / ngrok):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate800
                        )
                        Text(
                            text = "Run: cloudflared tunnel --url http://localhost:5000\nPaste the generated https://... URL into the Remote Web URL field above!",
                            fontSize = 11.sp,
                            color = DeepSlate800,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // 5. Persistent Background Android Service Card
        // ==========================================
        val clipboardManager = LocalClipboardManager.current
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, LightBlue100, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isBackgroundServiceActive) EmeraldSafe.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Sentinel Service",
                                tint = if (uiState.isBackgroundServiceActive) EmeraldSafe else AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Background Vigilance Sentinel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSlate800
                            )
                            Text(
                                text = if (uiState.isBackgroundServiceActive) "ACTIVE (With Low-Power WakeLock)" else "STOPPED",
                                fontSize = 11.sp,
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
                            checkedTrackColor = SkyBluePrimary,
                            uncheckedThumbColor = WhitePure,
                            uncheckedTrackColor = LightBlue100
                        ),
                        modifier = Modifier.testTag("bg_service_switch")
                    )
                }

                Text(
                    text = "Maintains a persistent system service so intruder alerts, motion snapshots, and lockscreen 1-tap buttons continue guarding your laptop even when your phone screen is off or you switch apps.",
                    fontSize = 12.sp,
                    color = MutedSlate500,
                    lineHeight = 16.sp
                )
            }
        }

        // ==========================================
        // 6. Laptop Lid-Close & Sleep Policy Guide
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, LightBlue100, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = WhitePure),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SkyBlueSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Power Policy",
                            tint = SkyBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Lid-Close & Sleep Prevention",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate800
                        )
                        Text(
                            text = "Keep surveillance awake when laptop lid is closed",
                            fontSize = 11.sp,
                            color = MutedSlate500
                        )
                    }
                }

                Text(
                    text = "Laptops naturally enter sleep mode when closed. Use these 1-line commands to keep the laptop security daemon and webcam operational even with the lid shut:",
                    fontSize = 12.sp,
                    color = DeepSlate800,
                    lineHeight = 16.sp
                )

                // Windows Command Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LightBlue50)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Windows (PowerCfg)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepSlate800)
                        OutlinedButton(
                            onClick = {
                                val cmd = "powercfg /setacvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0 && powercfg /setactive SCHEME_CURRENT"
                                clipboardManager.setText(AnnotatedString(cmd))
                                Toast.makeText(context, "Windows command copied!", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy CMD", fontSize = 10.sp)
                        }
                    }
                    Text(
                        text = "powercfg /setacvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0 && powercfg /setactive SCHEME_CURRENT",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SkyBlueDark
                    )
                }

                // macOS Command Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LightBlue50)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("macOS (Terminal)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepSlate800)
                        OutlinedButton(
                            onClick = {
                                val cmd = "sudo pmset -a disablesleep 1"
                                clipboardManager.setText(AnnotatedString(cmd))
                                Toast.makeText(context, "macOS command copied!", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy CMD", fontSize = 10.sp)
                        }
                    }
                    Text(
                        text = "sudo pmset -a disablesleep 1",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SkyBlueDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
