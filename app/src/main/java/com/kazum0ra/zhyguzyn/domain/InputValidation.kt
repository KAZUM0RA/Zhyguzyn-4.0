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
     */
    fun validate(
        odometerText: String,
        litersText: String,
        date: LocalDate,
        existing: List<Refuel>,
        editingId: Long?,
        tankCapacityLiters: Double,
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
                    else -> {
                        val others = existing.filter { it.id != editingId }
                        // Заправки, зроблені раніше за датою, мають менший або рівний пробіг,
                        // пізніші — більший або рівний. У межах одного дня порядок визначає пробіг.
                        val previous = others.filter { it.date < date }.maxOfOrNull { it.odometerKm }
                        val next = others.filter { it.date > date }.minOfOrNull { it.odometerKm }
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
}

/** Перевірка налаштувань бака. */
object SettingsValidator {

    const val MAX_CAPACITY_LITERS = 1000.0

    sealed interface Result {
        data class Valid(val settings: TankSettings) : Result
        data class Invalid(val capacityError: InputError?, val initialError: InputError?) : Result
    }

    fun validate(capacityText: String, initialText: String): Result {
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

        return if (capacityError == null && initialError == null) {
            Result.Valid(TankSettings(capacity, initial))
        } else {
            Result.Invalid(capacityError, initialError)
        }
    }
}
