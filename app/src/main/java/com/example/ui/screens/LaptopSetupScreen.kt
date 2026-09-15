package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LaptopConfigEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.Navy700
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.util.LaptopCompanionScript
import com.example.viewmodel.ConnectionState
import com.example.viewmodel.MonitorUiState

@Composable
fun LaptopSetupScreen(
    config: LaptopConfigEntity?,
    uiState: MonitorUiState,
    onSaveConfig: (String, String, Int, String) -> Unit,
    onTestConnection: () -> Unit,
    onToggleDemoMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var laptopName by remember { mutableStateOf(config?.laptopName ?: "My Laptop") }
    var ipAddress by remember { mutableStateOf(config?.ipAddress ?: "192.168.1.100") }
    var portText by remember { mutableStateOf((config?.port ?: 5000).toString()) }
    var pin by remember { mutableStateOf(config?.pin ?: "7890") }

    LaunchedEffect(config) {
        if (config != null) {
            laptopName = config.laptopName
            ipAddress = config.ipAddress
            portText = config.port.toString()
            pin = config.pin
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy900)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
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
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "LAPTOP LINK CONFIGURATION",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Pair your Tecno Camon 12 Air with your laptop",
                    fontSize = 12.sp,
                    color = Slate400
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Demo Mode vs Live Mode Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Navy700)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Safe Demo Simulation Mode",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (config?.isDemoMode == true)
                            "Testing features locally without laptop"
                        else
                            "Connecting to live laptop via local Wi-Fi",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = config?.isDemoMode ?: true,
                    onCheckedChange = { onToggleDemoMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Navy900,
                        checkedTrackColor = AmberWarning,
                        uncheckedThumbColor = CyanAccent,
                        uncheckedTrackColor = Navy700
                    ),
                    modifier = Modifier.testTag("demo_mode_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Navy700)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CONNECTION PARAMETERS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    fontFamily = FontFamily.Monospace
                )

                // Laptop Name
                OutlinedTextField(
                    value = laptopName,
                    onValueChange = { laptopName = it },
                    label = { Text("Laptop Identifier Name") },
                    leadingIcon = {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = CyanAccent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("laptop_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = Slate600,
                        focusedLabelColor = CyanAccent,
                        unfocusedLabelColor = Slate400
                    )
                )

                // IP Address
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Laptop Local IP Address") },
                    placeholder = { Text("e.g. 192.168.1.105") },
                    leadingIcon = {
                        Icon(Icons.Default.Lan, contentDescription = null, tint = CyanAccent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("laptop_ip_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = Slate600,
                        focusedLabelColor = CyanAccent,
                        unfocusedLabelColor = Slate400
                    )
                )

                // Port & PIN in a Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("laptop_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Slate600,
                            focusedLabelColor = CyanAccent,
                            unfocusedLabelColor = Slate400
                        )
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("PIN / Token") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = CyanAccent)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("laptop_pin_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Slate600,
                            focusedLabelColor = CyanAccent,
                            unfocusedLabelColor = Slate400
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons: Save & Test
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 5000
                            onSaveConfig(laptopName, ipAddress, port, pin)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("save_config_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            contentColor = Navy900
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Link", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onTestConnection,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("test_connection_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate600),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Ping")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // COMPANION SERVER SETUP GUIDE CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Navy700)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = EmeraldSafe,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Laptop Companion Server",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    // Copy Script Button
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Laptop Companion Script", LaptopCompanionScript.PYTHON_SCRIPT)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Server script copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Navy700,
                            contentColor = CyanAccent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("copy_script_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Script", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "To allow this app on your Tecno Camon 12 Air to stream video, broadcast warnings, and lock your screen, run this lightweight Python script on your laptop:",
                    color = Slate400,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Steps Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Navy900)
                        .border(1.dp, Navy700, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "1. Install Python from python.org on your laptop",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "2. Open Terminal / PowerShell and run:\n   pip install flask opencv-python pyttsx3 psutil",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "3. Save the copied script as laptop_guard.py and run:\n   python laptop_guard.py",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "4. Ensure both your Tecno phone and laptop are on the same Wi-Fi network (or turn on Mobile Hotspot on your Tecno phone and connect your laptop).",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
