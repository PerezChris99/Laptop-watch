package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "Motion Detected", "Laptop Unlocked", "Voice Warning", "Screen Locked", "Alarm Fired", "GPS Location Updated", "Hardware Tamper"
    val description: String,
    val snapshotUrl: String? = null,
    val warningIssued: String? = null,
    val wasLocked: Boolean = false,
    val severity: String = "WARNING", // "INFO", "WARNING", "ALERT"
    val category: String = "SECURITY", // "SECURITY", "USER_ACTION", "SYSTEM", "GPS", "TAMPER"
    val latitude: Double? = 40.7128902,
    val longitude: Double? = -74.0060804,
    val accuracyMeters: Float? = 1.0f,
    val locationName: String? = "Workstation Floor 3 • Desk 4B"
)
