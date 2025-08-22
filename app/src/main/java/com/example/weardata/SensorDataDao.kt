package com.example.weardata

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow // Importação para Flow


@Dao
interface SensorDataDao {

    @Insert
    suspend fun insert(sensorData: SensorDataEntity)


    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC")
    fun getAllSensorData(): Flow<List<SensorDataEntity>>


    @Query("SELECT * FROM sensor_data WHERE sensorType = :sensorType AND userState = :userState ORDER BY timestamp DESC")
    fun getSensorDataByTypeAndState(sensorType: String, userState: String): Flow<List<SensorDataEntity>>


    @Query("DELETE FROM sensor_data")
    suspend fun deleteAll()
}
