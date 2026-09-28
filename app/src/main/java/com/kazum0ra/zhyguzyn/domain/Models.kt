package com.kazum0ra.zhyguzyn.domain

import java.time.LocalDate

/** Одна заправка. Доменна модель, не залежить від Room. */
data class Refuel(
    val id: Long,
    val date: LocalDate,
    val odometerKm: Double,
    val liters: Double,
    val fullTank: Boolean,
)

/** Налаштування бака та лічильника пробігу. */
data class TankSettings(
    val capacityLiters: Double,
    val initialFuelLiters: Double,
    /**
     * Після якого значення лічильник обнуляється (наприклад, 1000 для лічильника 0–999,9).
     * 0 — лічильник не обнуляється (звичайний одометр).
     */
    val odometerRolloverKm: Double = 0.0,
) {
    val hasRollover: Boolean get() = odometerRolloverKm > 0.0

    companion object {
        val DEFAULT = TankSettings(capacityLiters = 50.0, initialFuelLiters = 0.0, odometerRolloverKm = 1000.0)
    }
}

/** Інтервал «від повного бака до повного бака». */
data class ConsumptionInterval(
    /** Заправка до повного, якою завершується інтервал. */
    val endRefuelId: Long,
    /** Пройдено км (з урахуванням обнулення лічильника). */
    val distanceKm: Double,
    val liters: Double,
) {
    val litersPer100Km: Double get() = liters / distanceKm * 100.0
}

/** Результат розрахунку за всіма заправками. */
data class FuelStats(
    val capacityLiters: Double,
    /** Значення, після якого лічильник обнуляється; 0 — не обнуляється. */
    val odometerRolloverKm: Double,
    val refuelCount: Int,
    /** Показник лічильника на момент останньої заправки. */
    val lastOdometerKm: Double?,
    val intervals: List<ConsumptionInterval>,
    /** Витрата за останній інтервал, л/100 км; null — даних замало. */
    val lastIntervalConsumption: Double?,
    /** Загальна середня витрата, л/100 км; null — даних замало. */
    val averageConsumption: Double?,
    /** Залишок одразу після останньої заправки, л; null — неможливо оцінити. */
    val fuelAfterLastRefuel: Double?,
) {
    /** Запас ходу на залишку після останньої заправки, км. */
    val rangeKm: Double?
        get() = FuelCalculator.rangeKm(fuelAfterLastRefuel, averageConsumption)

    val hasEntries: Boolean get() = refuelCount > 0

    /** Витрата інтервалу, що завершується заправкою з цим id (для історії). */
    fun consumptionEndingAt(refuelId: Long): Double? =
        intervals.firstOrNull { it.endRefuelId == refuelId }?.litersPer100Km
}

/** Результат швидкого розрахунку на екрані «Прикинути». */
sealed interface Estimate {
    data class Ok(
        val distanceKm: Double,
        val fuelLeftLiters: Double,
        val rangeKm: Double,
        val capacityLiters: Double,
    ) : Estimate

    /** Ще немає жодної заправки. */
    data object NoEntries : Estimate

    /** Замало даних для середньої витрати. */
    data object NotEnoughData : Estimate

    data class Invalid(val error: InputError, val limitKm: Double? = null) : Estimate
}

/** Помилки вводу. Тексти для користувача — у ресурсах (див. ui/components/Errors.kt). */
enum class InputError {
    EMPTY,
    NOT_A_NUMBER,
    MUST_BE_POSITIVE,
    NEGATIVE,
    /** Пробіг менший за пробіг попередньої заправки. */
    ODOMETER_LESS_THAN_PREVIOUS,
    /** Пробіг більший за пробіг наступної (за датою) заправки. */
    ODOMETER_GREATER_THAN_NEXT,
    /** Поточний пробіг менший за пробіг останньої заправки. */
    ODOMETER_BELOW_LAST_REFUEL,
    /** Показник більший за межу, на якій лічильник обнуляється. */
    ODOMETER_ABOVE_ROLLOVER,
    EXCEEDS_TANK,
    TOO_LARGE,
}
