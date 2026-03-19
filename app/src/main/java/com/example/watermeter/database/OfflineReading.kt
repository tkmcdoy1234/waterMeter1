package com.example.watermeter.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_readings")
data class OfflineReading(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val meterId: String,
    val reading: String,
    val usage: Double,
    val amount: Double,
    val timestamp: String,
    val reader: String,
    val photoBase64: String? = null,
    val locationPin: String? = null,
    val receiptText: String? = null
)
