package com.kazum0ra.zhyguzyn.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InputValidationTest {

    private val day1 = LocalDate.of(2026, 3, 1)
    private val day2 = LocalDate.of(2026, 3, 10)
    private val day3 = LocalDate.of(2026, 3, 20)

    private val existing = listOf(
        Refuel(id = 1, date = day1, odometerKm = 1_000.0, liters = 40.0, fullTank = true),
        Refuel(id = 2, date = day2, odometerKm = 1_500.0, liters = 35.0, fullTank = true),
        Refuel(id = 3, date = day3, odometerKm = 2_000.0, liters = 38.0, fullTank = true),
    )

    private fun validate(
        odometer: String,
        liters: String,
        date: LocalDate = day3.plusDays(1),
        editingId: Long? = null,
        capacity: Double = 50.0,
    ) = RefuelValidator.validate(odometer, liters, date, existing, editingId, capacity)

    // --- Розбір чисел ---

    @Test
    fun `parser accepts comma, dot and spaces`() {
        assertEquals(NumberParser.Result.Ok(45.5), NumberParser.parse("45,5"))
        assertEquals(NumberParser.Result.Ok(45.5), NumberParser.parse("45.5"))
        assertEquals(NumberParser.Result.Ok(12_345.0), NumberParser.parse("12 345"))
        assertEquals(NumberParser.Result.Ok(12_345.0), NumberParser.parse(" 12 345 "))
    }

    @Test
    fun `parser rejects empty and garbage`() {
        assertEquals(NumberParser.Result.Error(InputError.EMPTY), NumberParser.parse(""))
        assertEquals(NumberParser.Result.Error(InputError.EMPTY), NumberParser.parse("   "))
        assertEquals(NumberParser.Result.Error(InputError.NOT_A_NUMBER), NumberParser.parse("abc"))
        assertEquals(NumberParser.Result.Error(InputError.NOT_A_NUMBER), NumberParser.parse("1,2,3"))
        assertEquals(NumberParser.Result.Error(InputError.NOT_A_NUMBER), NumberParser.parse("NaN"))
        assertEquals(NumberParser.Result.Error(InputError.NOT_A_NUMBER), NumberParser.parse("Infinity"))
    }

    // --- Заправка ---

    @Test
    fun `valid new refuel`() {
        assertEquals(RefuelValidator.Result.Valid(2_450.0, 41.2), validate("2450", "41,2"))
    }

    @Test
    fun `odometer equal to previous is allowed`() {
        assertTrue(validate("2000", "5") is RefuelValidator.Result.Valid)
    }

    @Test
    fun `odometer less than previous is rejected`() {
        val result = validate("1999", "30") as RefuelValidator.Result.Invalid
        assertEquals(InputError.ODOMETER_LESS_THAN_PREVIOUS, result.odometerError)
        assertEquals(2_000.0, result.odometerLimitKm!!, 0.0)
    }

    @Test
    fun `backdated refuel must fit between neighbours`() {
        val between = LocalDate.of(2026, 3, 15)
        assertTrue(validate("1700", "30", date = between) is RefuelValidator.Result.Valid)
        val tooBig = validate("2100", "30", date = between) as RefuelValidator.Result.Invalid
        assertEquals(InputError.ODOMETER_GREATER_THAN_NEXT, tooBig.odometerError)
        assertEquals(2_000.0, tooBig.odometerLimitKm!!, 0.0)
        val tooSmall = validate("1400", "30", date = between) as RefuelValidator.Result.Invalid
        assertEquals(InputError.ODOMETER_LESS_THAN_PREVIOUS, tooSmall.odometerError)
    }

    @Test
    fun `editing a record does not compare it with itself`() {
        // Запис 3 (2000 км) правимо на 1800 — між 1500 і кінцем списку.
        assertTrue(validate("1800", "38", date = day3, editingId = 3) is RefuelValidator.Result.Valid)
        // Запис 2 не може стати більшим за наступний (2000).
        val result = validate("2100", "35", date = day2, editingId = 2) as RefuelValidator.Result.Invalid
        assertEquals(InputError.ODOMETER_GREATER_THAN_NEXT, result.odometerError)
    }

    @Test
    fun `new refuel on the same day as the last one cannot have smaller odometer`() {
        val result = validate("1900", "10", date = day3) as RefuelValidator.Result.Invalid
        assertEquals(InputError.ODOMETER_LESS_THAN_PREVIOUS, result.odometerError)
        assertEquals(2_000.0, result.odometerLimitKm!!, 0.0)
        assertTrue(validate("2000", "10", date = day3) is RefuelValidator.Result.Valid)
    }

    @Test
    fun `editing keeps the record's place among refuels of the same day`() {
        val sameDay = existing + Refuel(id = 4, date = day3, odometerKm = 2_300.0, liters = 20.0, fullTank = false)
        // Запис 3 (2000 км) стоїть перед записом 4 (2300 км) того ж дня: можна змінити в межах 1500..2300.
        assertTrue(
            RefuelValidator.validate("2100", "38", day3, sameDay, 3, 50.0) is RefuelValidator.Result.Valid,
        )
        val tooBig = RefuelValidator.validate("2400", "38", day3, sameDay, 3, 50.0) as RefuelValidator.Result.Invalid
        assertEquals(InputError.ODOMETER_GREATER_THAN_NEXT, tooBig.odometerError)
        assertEquals(2_300.0, tooBig.odometerLimitKm!!, 0.0)
        // Запис 4 не може стати меншим за запис 3.
        val tooSmall = RefuelValidator.validate("1990", "20", day3, sameDay, 4, 50.0) as RefuelValidator.Result.Invalid
        assertEquals(InputError.ODOMETER_LESS_THAN_PREVIOUS, tooSmall.odometerError)
    }

    @Test
    fun `liters must be positive`() {
        assertEquals(InputError.MUST_BE_POSITIVE, (validate("2500", "0") as RefuelValidator.Result.Invalid).litersError)
        assertEquals(InputError.MUST_BE_POSITIVE, (validate("2500", "-3") as RefuelValidator.Result.Invalid).litersError)
    }

    @Test
    fun `liters cannot exceed tank`() {
        val result = validate("2500", "55") as RefuelValidator.Result.Invalid
        assertEquals(InputError.EXCEEDS_TANK, result.litersError)
        assertEquals(null, result.odometerError)
    }

    @Test
    fun `both fields invalid are reported together`() {
        val result = validate("", "abc") as RefuelValidator.Result.Invalid
        assertEquals(InputError.EMPTY, result.odometerError)
        assertEquals(InputError.NOT_A_NUMBER, result.litersError)
    }

    @Test
    fun `negative and huge odometer are rejected`() {
        assertEquals(InputError.NEGATIVE, (validate("-1", "10") as RefuelValidator.Result.Invalid).odometerError)
        assertEquals(InputError.TOO_LARGE, (validate("100000000", "10") as RefuelValidator.Result.Invalid).odometerError)
    }

    @Test
    fun `first refuel has no previous odometer limit`() {
        val result = RefuelValidator.validate("5", "10", day1, emptyList(), null, 50.0)
        assertEquals(RefuelValidator.Result.Valid(5.0, 10.0), result)
    }

    // --- Лічильник, що обнуляється ---

    @Test
    fun `with rollover a smaller reading than previous is allowed`() {
        val trip = listOf(Refuel(id = 1, date = day1, odometerKm = 900.0, liters = 40.0, fullTank = true))
        assertEquals(
            RefuelValidator.Result.Valid(150.0, 35.0),
            RefuelValidator.validate("150", "35", day2, trip, null, 50.0, odometerRolloverKm = 1000.0),
        )
    }

    @Test
    fun `with rollover the reading must be below the rollover point`() {
        val result = RefuelValidator.validate("1000", "35", day2, emptyList(), null, 50.0, 1000.0)
            as RefuelValidator.Result.Invalid
        assertEquals(InputError.ODOMETER_ABOVE_ROLLOVER, result.odometerError)
        assertEquals(1000.0, result.odometerLimitKm!!, 0.0)
        assertTrue(
            RefuelValidator.validate("999,9", "35", day2, emptyList(), null, 50.0, 1000.0)
                is RefuelValidator.Result.Valid,
        )
    }

    @Test
    fun `previous reading hint follows date order with rollover`() {
        val trip = listOf(
            Refuel(id = 1, date = day1, odometerKm = 900.0, liters = 40.0, fullTank = true),
            Refuel(id = 2, date = day2, odometerKm = 150.0, liters = 35.0, fullTank = true),
        )
        assertEquals(150.0, RefuelValidator.previousOdometer(trip, null, day3, 1000.0)!!, 0.0)
        assertEquals(900.0, RefuelValidator.previousOdometer(trip, 2, day2, 1000.0)!!, 0.0)
        assertEquals(null, RefuelValidator.previousOdometer(trip, 1, day1, 1000.0))
        // Без обнулення — найбільший попередній пробіг.
        assertEquals(2_000.0, RefuelValidator.previousOdometer(existing, null, day3.plusDays(1), 0.0)!!, 0.0)
    }

    // --- Налаштування ---

    @Test
    fun `valid settings`() {
        assertEquals(
            SettingsValidator.Result.Valid(TankSettings(55.0, 12.5)),
            SettingsValidator.validate("55", "12,5"),
        )
    }

    @Test
    fun `empty initial fuel means zero`() {
        assertEquals(SettingsValidator.Result.Valid(TankSettings(40.0, 0.0)), SettingsValidator.validate("40", ""))
    }

    @Test
    fun `rollover setting`() {
        assertEquals(
            SettingsValidator.Result.Valid(TankSettings(40.0, 5.0, 1000.0)),
            SettingsValidator.validate("40", "5", "1000"),
        )
        assertEquals(SettingsValidator.Result.Valid(TankSettings(40.0, 5.0, 0.0)), SettingsValidator.validate("40", "5", ""))
        assertEquals(
            SettingsValidator.Result.Invalid(null, null, InputError.NEGATIVE),
            SettingsValidator.validate("40", "5", "-1"),
        )
    }

    @Test
    fun `manual consumption setting`() {
        assertEquals(
            SettingsValidator.Result.Valid(TankSettings(40.0, 5.0, 1000.0, 8.5)),
            SettingsValidator.validate("40", "5", "1000", "8,5"),
        )
        assertEquals(
            SettingsValidator.Result.Valid(TankSettings(40.0, 5.0, 1000.0, 0.0)),
            SettingsValidator.validate("40", "5", "1000", ""),
        )
        assertEquals(
            SettingsValidator.Result.Invalid(null, null, null, InputError.TOO_LARGE),
            SettingsValidator.validate("40", "5", "1000", "150"),
        )
        assertEquals(
            SettingsValidator.Result.Invalid(null, null, null, InputError.NOT_A_NUMBER),
            SettingsValidator.validate("40", "5", "1000", "abc"),
        )
    }

    @Test
    fun `invalid settings`() {
        assertEquals(
            SettingsValidator.Result.Invalid(InputError.MUST_BE_POSITIVE, null),
            SettingsValidator.validate("0", "0"),
        )
        assertEquals(
            SettingsValidator.Result.Invalid(null, InputError.EXCEEDS_TANK),
            SettingsValidator.validate("40", "41"),
        )
        assertEquals(
            SettingsValidator.Result.Invalid(InputError.NOT_A_NUMBER, InputError.NEGATIVE),
            SettingsValidator.validate("x", "-1"),
        )
        assertEquals(
            SettingsValidator.Result.Invalid(InputError.TOO_LARGE, null),
            SettingsValidator.validate("5000", "10"),
        )
    }

    // --- Версії ---

    @Test
    fun `version comparison`() {
        assertTrue(AppVersion.isNewer("v1.2.4", "1.2.3"))
        assertTrue(AppVersion.isNewer("v1.10.0", "1.9.9"))
        assertTrue(AppVersion.isNewer("v2.0.0", "1.99.99"))
        assertTrue(AppVersion.isNewer("V1.3", "1.2.9"))
        assertFalse(AppVersion.isNewer("v1.2.3", "1.2.3"))
        assertFalse(AppVersion.isNewer("v1.2.2", "1.2.3"))
        assertFalse(AppVersion.isNewer("v1.2.3", "1.2.3-debug"))
        assertFalse(AppVersion.isNewer("latest", "1.0.0"))
        assertTrue(AppVersion.isNewer("v0.0.2", "garbage"))
        assertEquals(listOf(1, 2, 0), AppVersion.parse("v1.2"))
    }
}
