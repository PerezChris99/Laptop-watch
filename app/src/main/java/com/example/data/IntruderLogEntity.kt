package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "Motion Detected", "Laptop Unlocked", "Voice Warning", "Screen Locked", "Alarm Fired"
    val description: String,
    val snapshotUrl: String? = null,
    val warningIssued: String? = null,
    val wasLocked: Boolean = false,
    val severity: String = "WARNING", // "INFO", "WARNING", "ALERT"
    val category: String = "SECURITY" // "SECURITY", "USER_ACTION", "SYSTEM"
)
