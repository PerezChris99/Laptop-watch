package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IntruderLogDao {
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<IntruderLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: IntruderLogEntity): Long

    @Query("DELETE FROM intruder_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM intruder_logs")
    suspend fun clearAllLogs()
}
