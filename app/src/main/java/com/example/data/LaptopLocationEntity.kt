package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Historical and live GPS location coordinates tracked for the laptop.
 * Records precise latitude, longitude, accuracy in meters, speed, altitude,
 * transport method (LAN / Web Cloud relay), and timestamps.
 */
@Entity(tableName = "laptop_locations")
data class LaptopLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 1.0f,
    val altitudeMeters: Double = 0.0,
    val speedKmh: Float = 0.0f,
    val provider: String = "GPS_HARDWARE", // "GPS_HARDWARE", "GEO_IP_BACKUP"
    val addressEstimate: String = "GPS Satellite Fix Acquired",
    val transport: String = "LAN", // "LAN" or "WEB"
    val isLiveFix: Boolean = true
)
