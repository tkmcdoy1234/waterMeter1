package com.example.watermeter.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consumers")
data class Consumer(
    @PrimaryKey val meterId: String,
    val name: String,
    val address: String,
    val phoneNumber: String,
    val password: String // Added for login
)
