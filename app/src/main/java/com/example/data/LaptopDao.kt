package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LaptopDao {
    @Query("SELECT * FROM laptop_config WHERE id = 1 LIMIT 1")
    fun getLaptopConfig(): Flow<LaptopConfigEntity?>

    @Query("SELECT * FROM laptop_config WHERE id = 1 LIMIT 1")
    suspend fun getLaptopConfigDirect(): LaptopConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: LaptopConfigEntity)

    @Query("UPDATE laptop_config SET isLocked = :locked WHERE id = 1")
    suspend fun updateLockStatus(locked: Boolean)

    @Query("UPDATE laptop_config SET isDemoMode = :demoMode WHERE id = 1")
    suspend fun updateDemoMode(demoMode: Boolean)

    @Query("UPDATE laptop_config SET isMotionArmed = :armed WHERE id = 1")
    suspend fun updateMotionArmed(armed: Boolean)

    @Query("UPDATE laptop_config SET motionSensitivity = :sensitivity WHERE id = 1")
    suspend fun updateMotionSensitivity(sensitivity: String)

    @Query("UPDATE laptop_config SET autoLockOnMotion = :autoLock WHERE id = 1")
    suspend fun updateAutoLockOnMotion(autoLock: Boolean)

    @Query("UPDATE laptop_config SET autoAlarmOnMotion = :autoAlarm WHERE id = 1")
    suspend fun updateAutoAlarmOnMotion(autoAlarm: Boolean)

    @Query("UPDATE laptop_config SET autoSnapOnMotion = :autoSnap WHERE id = 1")
    suspend fun updateAutoSnapOnMotion(autoSnap: Boolean)

    @Query("UPDATE laptop_config SET autoRecordOnMotion = :autoRecord WHERE id = 1")
    suspend fun updateAutoRecordOnMotion(autoRecord: Boolean)

    @Query("UPDATE laptop_config SET autoTtsOnMotion = :autoTts WHERE id = 1")
    suspend fun updateAutoTtsOnMotion(autoTts: Boolean)

    @Query("UPDATE laptop_config SET isAwayMode = :isAway WHERE id = 1")
    suspend fun updateAwayMode(isAway: Boolean)

    @Query("UPDATE laptop_config SET awayMotionSensitivity = :sensitivity WHERE id = 1")
    suspend fun updateAwaySensitivity(sensitivity: String)

    @Query("UPDATE laptop_config SET connectionMode = :mode WHERE id = 1")
    suspend fun updateConnectionMode(mode: String)

    @Query("UPDATE laptop_config SET activeTransport = :transport WHERE id = 1")
    suspend fun updateActiveTransport(transport: String)

    @Query("UPDATE laptop_config SET remoteWebUrl = :url WHERE id = 1")
    suspend fun updateRemoteWebUrl(url: String)
}
