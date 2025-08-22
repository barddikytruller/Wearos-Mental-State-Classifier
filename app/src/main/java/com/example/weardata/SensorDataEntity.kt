package com.example.weardata

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_data")
data class SensorDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sensorType: String,
    val value: Float,
    val status: Int,
    val ibi: Int?,
    val ibiQuality: Int?,
    val ibiListRaw: String?,
    val vfcRmssd: Double?,

    val timestamp: Long,
    val userState: String
)
