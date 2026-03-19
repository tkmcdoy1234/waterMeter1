package com.example.watermeter.database

import androidx.room.*

@Dao
interface ConsumerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun registerConsumer(consumer: Consumer)

    @Update
    suspend fun updateConsumer(consumer: Consumer)

    @Query("SELECT * FROM consumers WHERE meterId = :meterId LIMIT 1")
    suspend fun getConsumerByMeterId(meterId: String): Consumer?

    @Query("SELECT phoneNumber FROM consumers WHERE meterId = :meterId LIMIT 1")
    suspend fun getPhoneNumber(meterId: String): String?
}
