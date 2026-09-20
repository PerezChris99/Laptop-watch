package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM laptop_locations ORDER BY timestamp DESC")
    fun getAllLocations(): Flow<List<LaptopLocationEntity>>

    @Query("SELECT * FROM laptop_locations ORDER BY timestamp DESC LIMIT 1")
    fun getLatestLocation(): Flow<LaptopLocationEntity?>

    @Query("SELECT * FROM laptop_locations ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLocationDirect(): LaptopLocationEntity?

    @Query("SELECT * FROM laptop_locations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLocationsDirect(limit: Int): List<LaptopLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LaptopLocationEntity): Long

    @Query("DELETE FROM laptop_locations")
    suspend fun clearAllLocations()
}
