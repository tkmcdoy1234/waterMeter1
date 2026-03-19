package com.example.watermeter.database

import androidx.room.*

@Dao
interface OfflineReadingDao {
    @Insert
    suspend fun insertReading(reading: OfflineReading)

    @Query("SELECT * FROM offline_readings ORDER BY timestamp ASC")
    suspend fun getAllReadings(): List<OfflineReading>

    @Delete
    suspend fun deleteReading(reading: OfflineReading)

    @Query("DELETE FROM offline_readings")
    suspend fun deleteAllReadings()

    @Query("SELECT COUNT(*) FROM offline_readings")
    suspend fun getReadingCount(): Int
}
