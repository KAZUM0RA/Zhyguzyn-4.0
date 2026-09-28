package com.kazum0ra.zhyguzyn.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelDao {
    @Query("SELECT * FROM refuels ORDER BY date_epoch_day DESC, odometer_km DESC, id DESC")
    fun observeAll(): Flow<List<FuelEntry>>

    @Query("SELECT * FROM refuels WHERE id = :id")
    suspend fun getById(id: Long): FuelEntry?

    @Insert
    suspend fun insert(entry: FuelEntry): Long

    @Update
    suspend fun update(entry: FuelEntry)

    @Delete
    suspend fun delete(entry: FuelEntry)
}
