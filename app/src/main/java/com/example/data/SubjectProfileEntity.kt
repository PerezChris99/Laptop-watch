package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Facial Dossier & Biometric Trail for people detected around the laptop.
 * Tracks encounter count, last seen time, confidence, security threat classification,
 * behavior tags (e.g. "Frequent User", "Lurking near keyboard", "Nighttime intruder"),
 * and snapshot references.
 */
@Entity(tableName = "subject_profiles")
data class SubjectProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectTag: String, // e.g. "SUBJECT-01", "OWNER", "UNKNOWN_GUEST"
    val displayName: String, // e.g. "Authorized Owner", "Frequent Associate", "Unrecognized Individual"
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val encounterCount: Int = 1,
    val securityCategory: String = "AUTHORIZED", // "AUTHORIZED", "INVESTIGATE", "FLAGGED_THREAT"
    val behaviorNotes: String = "Regular workstation user. Normal posture detected.",
    val lastFaceSnapshotUrl: String? = null,
    val dwellDurationMinutes: Int = 5,
    val confidenceScore: Float = 0.94f
)
