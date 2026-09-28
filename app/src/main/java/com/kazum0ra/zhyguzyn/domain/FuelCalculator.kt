package com.kazum0ra.zhyguzyn.domain

/**
 * Уся логіка розрахунків пального. Чисті функції без залежностей від Android.
 *
 * Метод «від повного бака до повного бака»: якщо між двома заправками до повного
 * проїхано D км і за цей час (включно з другою повною заправкою) залито L літрів,
 * то витрата = L / D × 100.
 *
 * Лічильник може обнулятися (наприклад, рахує 0–999,9 км, а потім знову з нуля).
 * Тоді пробіг між двома показниками рахується з урахуванням переходу через нуль,
 * а між сусідніми заправками має бути менше одного повного оберту лічильника.
 */
object FuelCalculator {

    /**
     * Хронологічний порядок заправок. Якщо лічильник не обнуляється — за пробігом,
     * інакше пробіг не монотонний, тож за датою, а в межах дня — за порядком введення.
     */
    fun sortChronologically(refuels: List<Refuel>, odometerRolloverKm: Double): List<Refuel> =
        if (odometerRolloverKm > 0.0) {
            refuels.sortedWith(compareBy<Refuel>({ it.date }, { it.id }))
        } else {
            refuels.sortedWith(compareBy<Refuel>({ it.odometerKm }, { it.date }, { it.id }))
        }

    /** Пройдено км між двома показниками лічильника. */
    fun distanceKm(fromKm: Double, toKm: Double, odometerRolloverKm: Double): Double {
        val diff = toKm - fromKm
        if (odometerRolloverKm <= 0.0) return diff
        val wrapped = diff % odometerRolloverKm
        return if (wrapped < 0.0) wrapped + odometerRolloverKm else wrapped
    }

    fun calculate(refuels: List<Refuel>, settings: TankSettings): FuelStats {
        val capacity = settings.capacityLiters.coerceAtLeast(0.0)
        val rollover = settings.odometerRolloverKm.coerceAtLeast(0.0)
        val sorted = sortChronologically(refuels, rollover)
        val steps = stepDistances(sorted, rollover)
        val intervals = intervals(sorted, steps)
        val (average, source) = chooseAverage(sorted, steps, intervals, settings.manualConsumption)
        return FuelStats(
            capacityLiters = capacity,
            odometerRolloverKm = rollover,
            refuelCount = sorted.size,
            lastOdometerKm = sorted.lastOrNull()?.odometerKm,
            intervals = intervals,
            lastIntervalConsumption = intervals.lastOrNull()?.litersPer100Km,
            averageConsumption = average,
            consumptionSource = source,
            fuelAfterLastRefuel = fuelAfterLastRefuel(
                sorted,
                steps,
                settings.copy(capacityLiters = capacity),
                average,
            ),
        )
    }

    /** Мінімум заправок для приблизного розрахунку «від заправки до заправки». */
    const val MIN_REFUELS_FOR_ALL_REFUELS = 3

    /**
     * Середня витрата за пріоритетом:
     * 1) «від повного до повного», якщо є хоча б один такий інтервал;
     * 2) приблизно за всіма заправками, якщо їх щонайменше [MIN_REFUELS_FOR_ALL_REFUELS];
     * 3) норма з налаштувань, якщо задана.
     */
    private fun chooseAverage(
        sorted: List<Refuel>,
        steps: List<Double>,
        intervals: List<ConsumptionInterval>,
        manualConsumption: Double,
    ): Pair<Double?, ConsumptionSource?> {
        averageConsumption(intervals)?.let { return it to ConsumptionSource.FULL_TO_FULL }
        allRefuelsConsumption(sorted, steps)?.let { return it to ConsumptionSource.ALL_REFUELS }
        if (manualConsumption > 0.0) return manualConsumption to ConsumptionSource.MANUAL
        return null to null
    }

    /**
     * «Від заправки до заправки» за весь період: вважаємо, що кожною заправкою
     * доливали те, що спалили з попередньої. Літри першої заправки не рахуються —
     * їх спалено вже після неї. Похибка не більша за один бак на весь пробіг.
     */
    fun allRefuelsConsumption(sorted: List<Refuel>, steps: List<Double>): Double? {
        if (sorted.size < MIN_REFUELS_FOR_ALL_REFUELS) return null
        val distance = steps.drop(1).sum()
        if (distance <= 0.0) return null
        return sorted.drop(1).sumOf { it.liters } / distance * 100.0
    }

    /** steps[i] — км між заправкою i-1 та i (steps[0] = 0). */
    private fun stepDistances(sorted: List<Refuel>, rollover: Double): List<Double> =
        sorted.indices.map { i ->
            if (i == 0) 0.0
            else distanceKm(sorted[i - 1].odometerKm, sorted[i].odometerKm, rollover).coerceAtLeast(0.0)
        }

    /** Інтервали між послідовними заправками до повного (відсортований список). */
    private fun intervals(sorted: List<Refuel>, steps: List<Double>): List<ConsumptionInterval> {
        val result = mutableListOf<ConsumptionInterval>()
        var started = false
        var liters = 0.0
        var distance = 0.0
        for ((i, refuel) in sorted.withIndex()) {
            if (!started) {
                if (refuel.fullTank) started = true
                continue
            }
            liters += refuel.liters
            distance += steps[i]
            if (refuel.fullTank) {
                if (distance > 0.0) {
                    result += ConsumptionInterval(endRefuelId = refuel.id, distanceKm = distance, liters = liters)
                }
                // Бак знову повний — новий інтервал починається звідси.
                liters = 0.0
                distance = 0.0
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
    private fun fuelAfterLastRefuel(
        sorted: List<Refuel>,
        steps: List<Double>,
        settings: TankSettings,
        average: Double?,
    ): Double? {
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
            val distance = steps[i]
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

    /** Те саме, але за поточним показником лічильника. */
    fun estimateByOdometer(stats: FuelStats, currentOdometerKm: Double): Estimate {
        val last = stats.lastOdometerKm ?: return Estimate.NoEntries
        if (currentOdometerKm.isNaN() || currentOdometerKm.isInfinite()) {
            return Estimate.Invalid(InputError.NOT_A_NUMBER)
        }
        if (currentOdometerKm < 0.0) return Estimate.Invalid(InputError.NEGATIVE)
        val rollover = stats.odometerRolloverKm
        if (rollover > 0.0) {
            if (currentOdometerKm >= rollover) {
                return Estimate.Invalid(InputError.ODOMETER_ABOVE_ROLLOVER, limitKm = rollover)
            }
        } else if (currentOdometerKm < last) {
            return Estimate.Invalid(InputError.ODOMETER_BELOW_LAST_REFUEL, limitKm = last)
        }
        return estimateByDistance(stats, distanceKm(last, currentOdometerKm, rollover))
    }
}
