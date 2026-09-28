package com.kazum0ra.zhyguzyn.domain

/**
 * Уся логіка розрахунків пального. Чисті функції без залежностей від Android.
 *
 * Метод «від повного бака до повного бака»: якщо між двома заправками до повного
 * проїхано D км і за цей час (включно з другою повною заправкою) залито L літрів,
 * то витрата = L / D × 100.
 */
object FuelCalculator {

    /** Порядок заправок для розрахунків: пробіг, потім дата, потім id. */
    private val chronological = compareBy<Refuel>({ it.odometerKm }, { it.date }, { it.id })

    fun calculate(refuels: List<Refuel>, settings: TankSettings): FuelStats {
        val capacity = settings.capacityLiters.coerceAtLeast(0.0)
        val sorted = refuels.sortedWith(chronological)
        val intervals = intervals(sorted)
        val average = averageConsumption(intervals)
        return FuelStats(
            capacityLiters = capacity,
            refuelCount = sorted.size,
            lastOdometerKm = sorted.lastOrNull()?.odometerKm,
            intervals = intervals,
            lastIntervalConsumption = intervals.lastOrNull()?.litersPer100Km,
            averageConsumption = average,
            fuelAfterLastRefuel = fuelAfterLastRefuel(sorted, settings.copy(capacityLiters = capacity), average),
        )
    }

    /** Інтервали між послідовними заправками до повного (відсортований список). */
    fun intervals(sorted: List<Refuel>): List<ConsumptionInterval> {
        val result = mutableListOf<ConsumptionInterval>()
        var start: Refuel? = null
        var liters = 0.0
        for (refuel in sorted) {
            if (start == null) {
                if (refuel.fullTank) start = refuel
                continue
            }
            liters += refuel.liters
            if (refuel.fullTank) {
                if (refuel.odometerKm > start.odometerKm) {
                    result += ConsumptionInterval(
                        fromOdometerKm = start.odometerKm,
                        toOdometerKm = refuel.odometerKm,
                        endRefuelId = refuel.id,
                        liters = liters,
                    )
                }
                // Бак знову повний — новий інтервал починається звідси.
                start = refuel
                liters = 0.0
            }
        }
        return result
    }

    /** Загальна середня: сума літрів усіх інтервалів / сума кілометрів × 100. */
    fun averageConsumption(intervals: List<ConsumptionInterval>): Double? {
        val distance = intervals.sumOf { it.distanceKm }
        if (intervals.isEmpty() || distance <= 0.0) return null
        return intervals.sumOf { it.liters } / distance * 100.0
    }

    /**
     * Залишок у баку одразу після останньої заправки.
     *
     * Відлік іде від останньої заправки до повного (бак = об'єм), а якщо таких не було —
     * від початкового залишку на момент першої заправки. Між заправками пальне
     * списується за середньою витратою. Результат завжди в межах [0; об'єм бака].
     * null — якщо між заправками були пробіги, а середньої витрати ще немає.
     */
    fun fuelAfterLastRefuel(sorted: List<Refuel>, settings: TankSettings, average: Double?): Double? {
        if (sorted.isEmpty()) return null
        val capacity = settings.capacityLiters
        val lastFullIndex = sorted.indexOfLast { it.fullTank }

        var level: Double
        val startIndex: Int
        if (lastFullIndex >= 0) {
            level = capacity
            startIndex = lastFullIndex + 1
        } else {
            level = (settings.initialFuelLiters + sorted.first().liters).coerceIn(0.0, capacity)
            startIndex = 1
        }

        for (i in startIndex until sorted.size) {
            val distance = sorted[i].odometerKm - sorted[i - 1].odometerKm
            if (distance > 0.0) {
                if (average == null) return null
                level = (level - distance * average / 100.0).coerceAtLeast(0.0)
            }
            level = (level + sorted[i].liters).coerceIn(0.0, capacity)
        }
        return level
    }

    /** Запас ходу на заданому залишку, км. */
    fun rangeKm(fuelLiters: Double?, average: Double?): Double? {
        if (fuelLiters == null || average == null || average <= 0.0) return null
        return fuelLiters / average * 100.0
    }

    /** Залишок і запас ходу після того, як від останньої заправки проїхано [distanceKm]. */
    fun estimateByDistance(stats: FuelStats, distanceKm: Double): Estimate {
        if (!stats.hasEntries) return Estimate.NoEntries
        if (distanceKm.isNaN() || distanceKm.isInfinite()) return Estimate.Invalid(InputError.NOT_A_NUMBER)
        if (distanceKm < 0.0) return Estimate.Invalid(InputError.NEGATIVE)
        val average = stats.averageConsumption
        val fuelAfter = stats.fuelAfterLastRefuel
        if (average == null || fuelAfter == null) return Estimate.NotEnoughData

        val fuelLeft = (fuelAfter - distanceKm * average / 100.0).coerceIn(0.0, stats.capacityLiters)
        return Estimate.Ok(
            distanceKm = distanceKm,
            fuelLeftLiters = fuelLeft,
            rangeKm = rangeKm(fuelLeft, average) ?: 0.0,
            capacityLiters = stats.capacityLiters,
        )
    }

    /** Те саме, але за поточним показником одометра. */
    fun estimateByOdometer(stats: FuelStats, currentOdometerKm: Double): Estimate {
        val last = stats.lastOdometerKm ?: return Estimate.NoEntries
        if (currentOdometerKm.isNaN() || currentOdometerKm.isInfinite()) {
            return Estimate.Invalid(InputError.NOT_A_NUMBER)
        }
        if (currentOdometerKm < last) {
            return Estimate.Invalid(InputError.ODOMETER_BELOW_LAST_REFUEL, limitKm = last)
        }
        return estimateByDistance(stats, currentOdometerKm - last)
    }
}
