package com.kazum0ra.zhyguzyn.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kazum0ra.zhyguzyn.domain.Refuel
import java.time.LocalDate

@Entity(tableName = "refuels")
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Дата як кількість днів від 1970-01-01. */
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double,
    @ColumnInfo(name = "liters") val liters: Double,
    @ColumnInfo(name = "full_tank") val fullTank: Boolean,
)

fun FuelEntry.toDomain() = Refuel(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    odometerKm = odometerKm,
    liters = liters,
    fullTank = fullTank,
)

fun Refuel.toEntity() = FuelEntry(
    id = id,
    dateEpochDay = date.toEpochDay(),
    odometerKm = odometerKm,
    liters = liters,
    fullTank = fullTank,
)
