package com.example

import com.example.data.DiagnosticEvent
import com.example.data.LaptopConfigEntity
import com.example.viewmodel.MonitorTab
import com.example.viewmodel.MonitorUiState
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testDefaultConfig_motionDetectionArmed() {
        val config = LaptopConfigEntity()
        assertTrue("Motion detection should be armed by default", config.isMotionArmed)
        assertEquals("MEDIUM", config.motionSensitivity)
        assertTrue("Auto-lock on motion should be true by default", config.autoLockOnMotion)
        assertTrue("Auto-snap on motion should be true by default", config.autoSnapOnMotion)
    }

    @Test
    fun testDiagnosticEventCreation() {
        val event = DiagnosticEvent(
            tag = "NETWORK",
            status = "RECOVERED",
            message = "Connection timeout safely caught and auto-recovered"
        )
        assertEquals("NETWORK", event.tag)
        assertEquals("RECOVERED", event.status)
        assertTrue(event.timestamp > 0)
    }

    @Test
    fun testMonitorUiState_defaultsToDashboard() {
        val state = MonitorUiState()
        assertEquals(MonitorTab.DASHBOARD, state.activeTab)
        assertEquals("SECURE", state.threatLevel)
        assertEquals(0, state.caughtErrorsCount)
        assertFalse(state.motionAlertActive)
    }
}
