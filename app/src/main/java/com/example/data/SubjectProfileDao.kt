package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectProfileDao {
    @Query("SELECT * FROM subject_profiles ORDER BY lastSeenTimestamp DESC")
    fun getAllProfiles(): Flow<List<SubjectProfileEntity>>

    @Query("SELECT * FROM subject_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): SubjectProfileEntity?

    @Query("SELECT * FROM subject_profiles WHERE subjectTag = :tag LIMIT 1")
    suspend fun getProfileByTag(tag: String): SubjectProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SubjectProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: SubjectProfileEntity)

    @Query("UPDATE subject_profiles SET securityCategory = :category, behaviorNotes = :notes WHERE id = :id")
    suspend fun updateClassification(id: Long, category: String, notes: String)

    @Query("DELETE FROM subject_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("DELETE FROM subject_profiles")
    suspend fun clearAllProfiles()
}
