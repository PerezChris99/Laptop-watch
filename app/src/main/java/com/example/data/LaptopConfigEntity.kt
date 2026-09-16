package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "laptop_config")
data class LaptopConfigEntity(
    @PrimaryKey val id: Int = 1,
    val laptopName: String = "My Laptop",
    val ipAddress: String = "192.168.1.100",
    val port: Int = 5000,
    val pin: String = "7890",
    val remoteWebUrl: String = "",          // e.g. https://xxx.trycloudflare.com or public IP/domain
    val connectionMode: String = "AUTO",    // "AUTO", "LAN", "WEB"
    val activeTransport: String = "LAN",    // "LAN" or "WEB" (current working route)
    val isAwayMode: Boolean = false,        // Away Mode with heightened sensitivity
    val awayMotionSensitivity: String = "HIGH", // "HIGH" (5%) or "ULTRA" (2%) when away
    val isDemoMode: Boolean = true,
    val isLocked: Boolean = false,
    val isMotionArmed: Boolean = true,
    val motionSensitivity: String = "MEDIUM",
    val autoLockOnMotion: Boolean = true,
    val autoAlarmOnMotion: Boolean = false,
    val autoSnapOnMotion: Boolean = true,
    val autoRecordOnMotion: Boolean = true,
    val autoTtsOnMotion: Boolean = false,
    val ttsWarningPhrase: String = "Step away from this computer! You are being recorded!",
    val lastConnectedTime: Long = 0L,
    val lastSyncStatus: String = "Ready"
)
