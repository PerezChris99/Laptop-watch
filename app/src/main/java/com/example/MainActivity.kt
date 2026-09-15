package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.TopStatusHeader
import com.example.ui.screens.AlertDashboardScreen
import com.example.ui.screens.IntruderLogsScreen
import com.example.ui.screens.LaptopSetupScreen
import com.example.ui.screens.LiveCameraScreen
import com.example.ui.screens.VoiceIntercomScreen
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.LightBlue100
import com.example.ui.theme.LightBlueSoft
import com.example.ui.theme.MutedSlate500
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WhitePure
import com.example.ui.theme.WhiteSmoke
import com.example.viewmodel.LaptopMonitorViewModel
import com.example.viewmodel.MonitorTab

class MainActivity : ComponentActivity() {
    private val viewModel: LaptopMonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LaptopMonitorApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun LaptopMonitorApp(viewModel: LaptopMonitorViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config by viewModel.laptopConfig.collectAsStateWithLifecycle()
    val logs by viewModel.intruderLogs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.toastFeedback) {
        uiState.toastFeedback?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopStatusHeader(
                config = config,
                uiState = uiState,
                onLockClick = {
                    if (uiState.isLocked) {
                        viewModel.unlockLaptopSimulation()
                    } else {
                        viewModel.lockLaptopRemotely()
                    }
                },
                onAlarmClick = { viewModel.triggerDeterrentAlarm() },
                onMotionAlertClick = { viewModel.switchTab(MonitorTab.DASHBOARD) }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = WhitePure,
                modifier = Modifier
                    .navigationBarsPadding()
                    .border(androidx.compose.foundation.BorderStroke(1.dp, LightBlue100))
                    .testTag("bottom_nav_bar")
            ) {
                // Tab 1: Alert Dashboard
                NavigationBarItem(
                    selected = uiState.activeTab == MonitorTab.DASHBOARD,
                    onClick = { viewModel.switchTab(MonitorTab.DASHBOARD) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.motionAlertActive || uiState.threatLevel == "ALERT") {
                                    Badge(containerColor = CrimsonAlert, contentColor = Color.White) {
                                        Text("!")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Alert Dashboard"
                            )
                        }
                    },
                    label = { Text("Alerts", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SkyBlueDark,
                        selectedTextColor = SkyBlueDark,
                        indicatorColor = LightBlueSoft,
                        unselectedIconColor = MutedSlate500,
                        unselectedTextColor = MutedSlate500
                    ),
                    modifier = Modifier.testTag("tab_alerts")
                )

                // Tab 2: Camera
                NavigationBarItem(
                    selected = uiState.activeTab == MonitorTab.CAMERA,
                    onClick = { viewModel.switchTab(MonitorTab.CAMERA) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Camera Feed"
                        )
                    },
                    label = { Text("Webcam", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SkyBlueDark,
                        selectedTextColor = SkyBlueDark,
                        indicatorColor = LightBlueSoft,
                        unselectedIconColor = MutedSlate500,
                        unselectedTextColor = MutedSlate500
                    ),
                    modifier = Modifier.testTag("tab_camera")
                )

                // Tab 3: Intercom / Mic
                NavigationBarItem(
                    selected = uiState.activeTab == MonitorTab.INTERCOM,
                    onClick = { viewModel.switchTab(MonitorTab.INTERCOM) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Intercom"
                        )
                    },
                    label = { Text("Intercom", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SkyBlueDark,
                        selectedTextColor = SkyBlueDark,
                        indicatorColor = LightBlueSoft,
                        unselectedIconColor = MutedSlate500,
                        unselectedTextColor = MutedSlate500
                    ),
                    modifier = Modifier.testTag("tab_intercom")
                )

                // Tab 4: Intruder Logs
                NavigationBarItem(
                    selected = uiState.activeTab == MonitorTab.LOGS,
                    onClick = { viewModel.switchTab(MonitorTab.LOGS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (logs.isNotEmpty()) {
                                    Badge(containerColor = SkyBluePrimary, contentColor = Color.White) {
                                        Text(text = logs.size.coerceAtMost(99).toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Intruder Logs"
                            )
                        }
                    },
                    label = { Text("Intruders", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SkyBlueDark,
                        selectedTextColor = SkyBlueDark,
                        indicatorColor = LightBlueSoft,
                        unselectedIconColor = MutedSlate500,
                        unselectedTextColor = MutedSlate500
                    ),
                    modifier = Modifier.testTag("tab_logs")
                )

                // Tab 5: Setup
                NavigationBarItem(
                    selected = uiState.activeTab == MonitorTab.SETUP,
                    onClick = { viewModel.switchTab(MonitorTab.SETUP) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Link Settings"
                        )
                    },
                    label = { Text("Link", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SkyBlueDark,
                        selectedTextColor = SkyBlueDark,
                        indicatorColor = LightBlueSoft,
                        unselectedIconColor = MutedSlate500,
                        unselectedTextColor = MutedSlate500
                    ),
                    modifier = Modifier.testTag("tab_setup")
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WhiteSmoke
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                MonitorTab.DASHBOARD -> {
                    AlertDashboardScreen(
                        uiState = uiState,
                        config = config,
                        intruderLogs = logs,
                        onToggleMotionArmed = { viewModel.toggleMotionArmed() },
                        onSetSensitivity = { sensitivity -> viewModel.setMotionSensitivity(sensitivity) },
                        onToggleAutoLock = { viewModel.toggleAutoLockOnMotion() },
                        onToggleAutoAlarm = { viewModel.toggleAutoAlarmOnMotion() },
                        onToggleAutoSnap = { viewModel.toggleAutoSnapOnMotion() },
                        onToggleAutoTts = { viewModel.toggleAutoTtsOnMotion() },
                        onSimulateMotion = { viewModel.simulateIntruderMotion() },
                        onDismissAlert = { viewModel.dismissMotionAlert() },
                        onLockLaptop = { viewModel.lockLaptopRemotely() },
                        onUnlockLaptop = { viewModel.unlockLaptopSimulation() },
                        onTriggerAlarm = { viewModel.triggerDeterrentAlarm() },
                        onNavigateToTab = { tab -> viewModel.switchTab(tab) },
                        onToggleAwayMode = { viewModel.toggleAwayMode() },
                        onSetAwaySensitivity = { viewModel.setAwaySensitivity(it) }
                    )
                }

                MonitorTab.CAMERA -> {
                    LiveCameraScreen(
                        uiState = uiState,
                        cameraUrl = viewModel.getCameraSnapshotUrl(),
                        onCaptureSnapshot = { viewModel.captureManualSnapshot() },
                        onLockToggle = {
                            if (uiState.isLocked) {
                                viewModel.unlockLaptopSimulation()
                            } else {
                                viewModel.lockLaptopRemotely()
                            }
                        },
                        onRefreshCamera = { viewModel.refreshCameraFrame() },
                        onNavigateToIntercom = { viewModel.switchTab(MonitorTab.INTERCOM) }
                    )
                }

                MonitorTab.INTERCOM -> {
                    VoiceIntercomScreen(
                        uiState = uiState,
                        onStartRecording = { viewModel.startVoiceRecording() },
                        onStopAndSendRecording = { viewModel.stopAndSendVoiceRecording() },
                        onCancelRecording = { viewModel.cancelVoiceRecording() },
                        onSendTTS = { msg -> viewModel.sendTTSWarning(msg) },
                        onTriggerAlarm = { viewModel.triggerDeterrentAlarm() }
                    )
                }

                MonitorTab.LOGS -> {
                    IntruderLogsScreen(
                        logs = logs,
                        onDeleteLog = { id -> viewModel.deleteLog(id) },
                        onClearAllLogs = { viewModel.clearAllLogs() }
                    )
                }

                MonitorTab.SETUP -> {
                    LaptopSetupScreen(
                        config = config,
                        uiState = uiState,
                        onSaveConfig = { name, ip, port, pin, webUrl, mode ->
                            viewModel.updateConfig(name, ip, port, pin, webUrl, mode)
                        },
                        onTestConnection = { viewModel.testConnection() },
                        onToggleDemoMode = { viewModel.toggleDemoMode() },
                        onToggleAwayMode = { viewModel.toggleAwayMode() },
                        onSetAwaySensitivity = { viewModel.setAwaySensitivity(it) }
                    )
                }
            }
        }
    }
}
