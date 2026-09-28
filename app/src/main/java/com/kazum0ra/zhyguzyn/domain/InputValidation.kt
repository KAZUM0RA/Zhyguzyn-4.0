package com.kazum0ra.zhyguzyn.domain

import java.time.LocalDate

/** Розбір чисел, введених користувачем: допускаються кома та пробіли («12 345,6»). */
object NumberParser {
    private val spaces = Regex("[\\s\\u00A0\\u202F]")

    sealed interface Result {
        data class Ok(val value: Double) : Result
        data class Error(val error: InputError) : Result
    }

    fun parse(text: String): Result {
        val normalized = text.replace(spaces, "").replace(',', '.')
        if (normalized.isEmpty()) return Result.Error(InputError.EMPTY)
        val value = normalized.toDoubleOrNull()
        if (value == null || value.isNaN() || value.isInfinite()) {
            return Result.Error(InputError.NOT_A_NUMBER)
        }
        return Result.Ok(value)
    }
}

/** Перевірка форми заправки. */
object RefuelValidator {

    /** Верхня межа одометра — захист від випадкових зайвих цифр. */
    const val MAX_ODOMETER_KM = 9_999_999.0

    sealed interface Result {
        data class Valid(val odometerKm: Double, val liters: Double) : Result
        data class Invalid(
            val odometerError: InputError?,
            val litersError: InputError?,
            /** Межа, з якою порівнювався пробіг (для тексту помилки). */
            val odometerLimitKm: Double? = null,
        ) : Result
    }

    /**
     * @param existing усі збережені заправки
     * @param editingId id запису, що редагується (його не порівнюємо сам із собою)
     * @param odometerRolloverKm після якого значення лічильник обнуляється; 0 — не обнуляється.
     *   Якщо обнуляється, показник може бути меншим за попередній (лічильник пройшов через нуль),
     *   тому перевіряється лише, що він у межах лічильника.
     */
    fun validate(
        odometerText: String,
        litersText: String,
        date: LocalDate,
        existing: List<Refuel>,
        editingId: Long?,
        tankCapacityLiters: Double,
        odometerRolloverKm: Double = 0.0,
    ): Result {
        var odometerError: InputError? = null
        var odometerLimit: Double? = null
        var odometer = 0.0
        when (val parsed = NumberParser.parse(odometerText)) {
            is NumberParser.Result.Error -> odometerError = parsed.error
            is NumberParser.Result.Ok -> {
                odometer = parsed.value
                when {
                    odometer < 0.0 -> odometerError = InputError.NEGATIVE
                    odometer > MAX_ODOMETER_KM -> odometerError = InputError.TOO_LARGE
                    odometerRolloverKm > 0.0 -> if (odometer >= odometerRolloverKm) {
                        odometerError = InputError.ODOMETER_ABOVE_ROLLOVER
                        odometerLimit = odometerRolloverKm
                    }
                    else -> {
                        val (before, after) = neighbours(existing, editingId, date)
                        val previous = before.maxOfOrNull { it.odometerKm }
                        val next = after.minOfOrNull { it.odometerKm }
                        if (previous != null && odometer < previous) {
                            odometerError = InputError.ODOMETER_LESS_THAN_PREVIOUS
                            odometerLimit = previous
                        } else if (next != null && odometer > next) {
                            odometerError = InputError.ODOMETER_GREATER_THAN_NEXT
                            odometerLimit = next
                        }
                    }
                }
            }
        }

        var litersError: InputError? = null
        var liters = 0.0
        when (val parsed = NumberParser.parse(litersText)) {
            is NumberParser.Result.Error -> litersError = parsed.error
            is NumberParser.Result.Ok -> {
                liters = parsed.value
                when {
                    liters <= 0.0 -> litersError = InputError.MUST_BE_POSITIVE
                    tankCapacityLiters > 0.0 && liters > tankCapacityLiters -> litersError = InputError.EXCEEDS_TANK
                }
            }
        }

        return if (odometerError == null && litersError == null) {
            Result.Valid(odometer, liters)
        } else {
            Result.Invalid(odometerError, litersError, odometerLimit)
        }
    }

    /**
     * Заправки до й після тієї, що вводиться. Раніші за датою — «до», пізніші — «після».
     * Заправки того самого дня вважаються попередніми (нову вводять після них),
     * крім випадку, коли редагується запис без зміни дати: тоді зберігається його
     * поточне місце серед заправок цього дня.
     */
    fun neighbours(existing: List<Refuel>, editingId: Long?, date: LocalDate): Pair<List<Refuel>, List<Refuel>> {
        val original = existing.firstOrNull { it.id == editingId }
        val others = existing.filter { it.id != editingId }
        val before = others.filter { it.date < date }.toMutableList()
        val after = others.filter { it.date > date }.toMutableList()
        for (refuel in others.filter { it.date == date }) {
            val isAfterOriginal = original != null && original.date == date &&
                (refuel.odometerKm > original.odometerKm ||
                    (refuel.odometerKm == original.odometerKm && refuel.id > original.id))
            if (isAfterOriginal) after += refuel else before += refuel
        }
        return before to after
    }

    /** Показник попередньої заправки — підказка під полем вводу. */
    fun previousOdometer(
        existing: List<Refuel>,
        editingId: Long?,
        date: LocalDate,
        odometerRolloverKm: Double,
    ): Double? {
        if (odometerRolloverKm > 0.0) {
            val others = existing.filter { it.id != editingId }
            val sorted = FuelCalculator.sortChronologically(others, odometerRolloverKm)
            val original = existing.firstOrNull { it.id == editingId }
            return if (original != null && original.date == date) {
                sorted.lastOrNull { it.date < date || (it.date == date && it.id < original.id) }
            } else {
                sorted.lastOrNull { it.date <= date }
            }?.odometerKm
        }
        return neighbours(existing, editingId, date).first.maxOfOrNull { it.odometerKm }
    }
}

/** Перевірка налаштувань бака. */
object SettingsValidator {

    const val MAX_CAPACITY_LITERS = 1000.0

    sealed interface Result {
        data class Valid(val settings: TankSettings) : Result
        data class Invalid(
            val capacityError: InputError?,
            val initialError: InputError?,
            val rolloverError: InputError? = null,
            val manualConsumptionError: InputError? = null,
        ) : Result
    }

    /** Максимальне значення, на якому лічильник може обнулятися. */
    const val MAX_ROLLOVER_KM = 1_000_000.0

    /** @param rolloverText після якого значення лічильник обнуляється; порожньо або 0 — не обнуляється. */
    /** Максимальна норма витрати, л/100 км. */
    const val MAX_MANUAL_CONSUMPTION = 100.0

    /** @param manualConsumptionText норма витрати, л/100 км; порожньо або 0 — не задано. */
    fun validate(
        capacityText: String,
        initialText: String,
        rolloverText: String = "",
        manualConsumptionText: String = "",
    ): Result {
        var capacityError: InputError? = null
        var capacity = 0.0
        when (val parsed = NumberParser.parse(capacityText)) {
            is NumberParser.Result.Error -> capacityError = parsed.error
            is NumberParser.Result.Ok -> {
                capacity = parsed.value
                if (capacity <= 0.0) capacityError = InputError.MUST_BE_POSITIVE
                else if (capacity > MAX_CAPACITY_LITERS) capacityError = InputError.TOO_LARGE
            }
        }

        var initialError: InputError? = null
        var initial = 0.0
        // Порожній початковий залишок вважаємо нулем.
        if (initialText.isNotBlank()) {
            when (val parsed = NumberParser.parse(initialText)) {
                is NumberParser.Result.Error -> initialError = parsed.error
                is NumberParser.Result.Ok -> {
                    initial = parsed.value
                    if (initial < 0.0) initialError = InputError.NEGATIVE
                    else if (capacityError == null && initial > capacity) initialError = InputError.EXCEEDS_TANK
                }
            }
        }

        var rolloverError: InputError? = null
        var rollover = 0.0
        if (rolloverText.isNotBlank()) {
            when (val parsed = NumberParser.parse(rolloverText)) {
                is NumberParser.Result.Error -> rolloverError = parsed.error
                is NumberParser.Result.Ok -> {
                    rollover = parsed.value
                    if (rollover < 0.0) rolloverError = InputError.NEGATIVE
                    else if (rollover > MAX_ROLLOVER_KM) rolloverError = InputError.TOO_LARGE
                }
            }
        }

        var manualError: InputError? = null
        var manual = 0.0
        if (manualConsumptionText.isNotBlank()) {
            when (val parsed = NumberParser.parse(manualConsumptionText)) {
                is NumberParser.Result.Error -> manualError = parsed.error
                is NumberParser.Result.Ok -> {
                    manual = parsed.value
                    if (manual < 0.0) manualError = InputError.NEGATIVE
                    else if (manual > MAX_MANUAL_CONSUMPTION) manualError = InputError.TOO_LARGE
                }
            }
        }

        return if (capacityError == null && initialError == null && rolloverError == null && manualError == null) {
            Result.Valid(TankSettings(capacity, initial, rollover, manual))
        } else {
            Result.Invalid(capacityError, initialError, rolloverError, manualError)
        }
    }
}
