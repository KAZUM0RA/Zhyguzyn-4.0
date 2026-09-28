package com.kazum0ra.zhyguzyn.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FuelCalculatorTest {

    private val tank = TankSettings(capacityLiters = 50.0, initialFuelLiters = 10.0)
    private val start = LocalDate.of(2026, 1, 1)
    private var nextId = 1L

    private fun refuel(odometer: Double, liters: Double, full: Boolean = true, day: Long = nextId) =
        Refuel(id = nextId++, date = start.plusDays(day), odometerKm = odometer, liters = liters, fullTank = full)

    private fun assertClose(expected: Double, actual: Double?, delta: Double = 1e-6) {
        requireNotNull(actual) { "Очікували $expected, отримали null" }
        assertEquals(expected, actual, delta)
    }

    // --- Звичайні випадки ---

    @Test
    fun `two full refuels give consumption for the interval`() {
        val stats = FuelCalculator.calculate(
            listOf(refuel(10_000.0, 40.0), refuel(10_500.0, 35.0)),
            tank,
        )
        assertClose(7.0, stats.lastIntervalConsumption)
        assertClose(7.0, stats.averageConsumption)
        assertClose(50.0, stats.fuelAfterLastRefuel)
        assertClose(50.0 / 7.0 * 100.0, stats.rangeKm)
        assertEquals(10_500.0, stats.lastOdometerKm!!, 0.0)
    }

    @Test
    fun `average is total liters over total distance, last interval is separate`() {
        val stats = FuelCalculator.calculate(
            listOf(
                refuel(0.0, 45.0),
                refuel(500.0, 30.0), // 6 л/100
                refuel(1_000.0, 45.0), // 9 л/100
            ),
            tank,
        )
        assertClose(9.0, stats.lastIntervalConsumption)
        assertClose(75.0 / 1_000.0 * 100.0, stats.averageConsumption) // 7.5
        assertEquals(2, stats.intervals.size)
    }

    @Test
    fun `order of input does not matter`() {
        val a = refuel(0.0, 45.0)
        val b = refuel(500.0, 30.0)
        val c = refuel(1_000.0, 45.0)
        val sorted = FuelCalculator.calculate(listOf(a, b, c), tank)
        val shuffled = FuelCalculator.calculate(listOf(c, a, b), tank)
        assertEquals(sorted, shuffled)
    }

    @Test
    fun `history shows consumption for refuel that closes an interval`() {
        val first = refuel(0.0, 45.0)
        val second = refuel(400.0, 32.0)
        val stats = FuelCalculator.calculate(listOf(first, second), tank)
        assertNull(stats.consumptionEndingAt(first.id))
        assertClose(8.0, stats.consumptionEndingAt(second.id))
    }

    // --- Неповний бак ---

    @Test
    fun `partial refuels between full ones are counted in the interval`() {
        val stats = FuelCalculator.calculate(
            listOf(
                refuel(1_000.0, 40.0, full = true),
                refuel(1_300.0, 15.0, full = false),
                refuel(1_600.0, 30.0, full = true),
            ),
            tank,
        )
        // (15 + 30) / 600 * 100
        assertClose(7.5, stats.lastIntervalConsumption)
        assertClose(7.5, stats.averageConsumption)
        assertEquals(1, stats.intervals.size)
    }

    @Test
    fun `partial refuels before the first full one are ignored for consumption`() {
        val stats = FuelCalculator.calculate(
            listOf(
                refuel(0.0, 20.0, full = false),
                refuel(200.0, 30.0, full = true),
                refuel(700.0, 40.0, full = true),
            ),
            tank,
        )
        assertClose(8.0, stats.averageConsumption)
    }

    @Test
    fun `fuel after partial refuel is estimated from last full tank`() {
        val stats = FuelCalculator.calculate(
            listOf(
                refuel(0.0, 50.0, full = true),
                refuel(1_000.0, 40.0, full = true), // 40 / 1000 * 100 = 4 л/100
                refuel(1_500.0, 10.0, full = false),
            ),
            tank,
        )
        assertClose(4.0, stats.averageConsumption)
        // 50 - 500 * 4 / 100 + 10 = 40
        assertClose(40.0, stats.fuelAfterLastRefuel)
        assertClose(1_000.0, stats.rangeKm)
    }

    @Test
    fun `fuel never exceeds tank capacity`() {
        val stats = FuelCalculator.calculate(
            listOf(
                refuel(0.0, 50.0, full = true),
                refuel(1_000.0, 50.0, full = true), // 5 л/100
                refuel(1_010.0, 45.0, full = false), // 50 - 0.5 + 45 > 50
            ),
            tank,
        )
        assertClose(50.0, stats.fuelAfterLastRefuel)
    }

    @Test
    fun `fuel never goes below zero between refuels`() {
        val stats = FuelCalculator.calculate(
            listOf(
                refuel(0.0, 50.0, full = true),
                refuel(500.0, 50.0, full = true), // 10 л/100
                refuel(1_500.0, 5.0, full = false), // списали б 100 л з 50
            ),
            tank,
        )
        assertClose(5.0, stats.fuelAfterLastRefuel)
    }

    @Test
    fun `without any full tank the initial fuel is used`() {
        val stats = FuelCalculator.calculate(listOf(refuel(1_000.0, 20.0, full = false)), tank)
        assertClose(30.0, stats.fuelAfterLastRefuel) // 10 + 20
        assertNull(stats.averageConsumption)
        assertNull(stats.rangeKm)
    }

    @Test
    fun `initial fuel plus refuel is clamped to capacity`() {
        val stats = FuelCalculator.calculate(
            listOf(refuel(1_000.0, 45.0, full = false)),
            TankSettings(capacityLiters = 50.0, initialFuelLiters = 20.0),
        )
        assertClose(50.0, stats.fuelAfterLastRefuel)
    }

    // --- Мало даних ---

    @Test
    fun `no refuels`() {
        val stats = FuelCalculator.calculate(emptyList(), tank)
        assertFalse(stats.hasEntries)
        assertNull(stats.averageConsumption)
        assertNull(stats.lastIntervalConsumption)
        assertNull(stats.fuelAfterLastRefuel)
        assertNull(stats.rangeKm)
        assertEquals(Estimate.NoEntries, FuelCalculator.estimateByDistance(stats, 100.0))
        assertEquals(Estimate.NoEntries, FuelCalculator.estimateByOdometer(stats, 100.0))
    }

    @Test
    fun `single full refuel is not enough for consumption but fuel level is known`() {
        val stats = FuelCalculator.calculate(listOf(refuel(1_000.0, 40.0)), tank)
        assertTrue(stats.hasEntries)
        assertNull(stats.averageConsumption)
        assertClose(50.0, stats.fuelAfterLastRefuel)
        assertNull(stats.rangeKm)
        assertEquals(Estimate.NotEnoughData, FuelCalculator.estimateByDistance(stats, 100.0))
    }

    @Test
    fun `only partial refuels with distance give unknown fuel level`() {
        val stats = FuelCalculator.calculate(
            listOf(refuel(1_000.0, 20.0, full = false), refuel(1_300.0, 20.0, full = false)),
            tank,
        )
        assertNull(stats.averageConsumption)
        assertNull(stats.fuelAfterLastRefuel)
        assertEquals(Estimate.NotEnoughData, FuelCalculator.estimateByOdometer(stats, 1_400.0))
    }

    @Test
    fun `two full refuels at the same odometer give no interval`() {
        val stats = FuelCalculator.calculate(listOf(refuel(1_000.0, 40.0), refuel(1_000.0, 2.0)), tank)
        assertTrue(stats.intervals.isEmpty())
        assertNull(stats.averageConsumption)
    }

    // --- Швидкий розрахунок ---

    private fun statsWith8LitersPer100() = FuelCalculator.calculate(
        listOf(refuel(10_000.0, 50.0), refuel(10_500.0, 40.0)), // 8 л/100, бак повний (50 л)
        tank,
    )

    @Test
    fun `estimate by distance`() {
        val result = FuelCalculator.estimateByDistance(statsWith8LitersPer100(), 250.0) as Estimate.Ok
        assertClose(30.0, result.fuelLeftLiters) // 50 - 20
        assertClose(375.0, result.rangeKm)
    }

    @Test
    fun `estimate by odometer uses distance from last refuel`() {
        val result = FuelCalculator.estimateByOdometer(statsWith8LitersPer100(), 10_750.0) as Estimate.Ok
        assertClose(250.0, result.distanceKm)
        assertClose(30.0, result.fuelLeftLiters)
    }

    @Test
    fun `estimate right after refuel shows full tank`() {
        val result = FuelCalculator.estimateByDistance(statsWith8LitersPer100(), 0.0) as Estimate.Ok
        assertClose(50.0, result.fuelLeftLiters)
        assertClose(625.0, result.rangeKm)
    }

    @Test
    fun `estimate never goes below zero`() {
        val result = FuelCalculator.estimateByDistance(statsWith8LitersPer100(), 5_000.0) as Estimate.Ok
        assertClose(0.0, result.fuelLeftLiters)
        assertClose(0.0, result.rangeKm)
    }

    // --- Некоректний ввід ---

    @Test
    fun `estimate rejects odometer below last refuel`() {
        val result = FuelCalculator.estimateByOdometer(statsWith8LitersPer100(), 10_000.0)
        assertEquals(Estimate.Invalid(InputError.ODOMETER_BELOW_LAST_REFUEL, 10_500.0), result)
    }

    @Test
    fun `estimate rejects negative distance and non finite values`() {
        val stats = statsWith8LitersPer100()
        assertEquals(Estimate.Invalid(InputError.NEGATIVE), FuelCalculator.estimateByDistance(stats, -1.0))
        assertEquals(Estimate.Invalid(InputError.NOT_A_NUMBER), FuelCalculator.estimateByDistance(stats, Double.NaN))
        assertEquals(
            Estimate.Invalid(InputError.NOT_A_NUMBER),
            FuelCalculator.estimateByOdometer(stats, Double.POSITIVE_INFINITY),
        )
    }

    @Test
    fun `negative capacity in settings is treated as zero`() {
        val stats = FuelCalculator.calculate(listOf(refuel(0.0, 10.0)), TankSettings(-5.0, 0.0))
        assertClose(0.0, stats.fuelAfterLastRefuel)
    }

    // --- Лічильник, що обнуляється після 1000 км ---

    private val trip = TankSettings(capacityLiters = 50.0, initialFuelLiters = 10.0, odometerRolloverKm = 1000.0)

    @Test
    fun `distance wraps around the rollover point`() {
        assertEquals(150.0, FuelCalculator.distanceKm(900.0, 50.0, 1000.0), 1e-9)
        assertEquals(100.0, FuelCalculator.distanceKm(200.0, 300.0, 1000.0), 1e-9)
        assertEquals(0.0, FuelCalculator.distanceKm(420.0, 420.0, 1000.0), 1e-9)
        assertEquals(150.1, FuelCalculator.distanceKm(950.2, 100.3, 1000.0), 1e-9)
        // Без обнулення — звичайна різниця.
        assertEquals(500.0, FuelCalculator.distanceKm(10_000.0, 10_500.0, 0.0), 1e-9)
    }

    @Test
    fun `consumption across counter reset`() {
        val stats = FuelCalculator.calculate(
            listOf(
                refuel(700.0, 40.0), // день 1
                refuel(200.0, 35.0), // день 2: 700 → 999,9 → 0 → 200 = 500 км
            ),
            trip,
        )
        assertClose(7.0, stats.averageConsumption)
        assertClose(50.0, stats.fuelAfterLastRefuel)
        assertEquals(200.0, stats.lastOdometerKm!!, 0.0)
    }

    @Test
    fun `several resets with partial refuel in between`() {
        val stats = FuelCalculator.calculate(
            listOf(
                refuel(800.0, 45.0, full = true),
                refuel(100.0, 20.0, full = false), // +300
                refuel(500.0, 25.0, full = true), // +400, разом 700 км, 45 л
                refuel(100.0, 36.0, full = true), // +600
            ),
            trip,
        )
        assertEquals(2, stats.intervals.size)
        assertClose(45.0 / 700.0 * 100.0, stats.intervals[0].litersPer100Km)
        assertClose(6.0, stats.lastIntervalConsumption)
        assertClose(81.0 / 1300.0 * 100.0, stats.averageConsumption)
    }

    @Test
    fun `with rollover refuels are ordered by date, not by counter value`() {
        val late = refuel(100.0, 30.0, day = 5)
        val early = refuel(600.0, 40.0, day = 1)
        val stats = FuelCalculator.calculate(listOf(late, early), trip)
        // 600 → 100 через нуль = 500 км
        assertClose(6.0, stats.averageConsumption)
        assertEquals(100.0, stats.lastOdometerKm!!, 0.0)
    }

    private fun tripStats() = FuelCalculator.calculate(
        listOf(refuel(700.0, 40.0), refuel(200.0, 40.0)), // 8 л/100, останній показник 200
        trip,
    )

    @Test
    fun `estimate by counter reading after reset`() {
        val result = FuelCalculator.estimateByOdometer(tripStats(), 450.0) as Estimate.Ok
        assertClose(250.0, result.distanceKm)
        assertClose(30.0, result.fuelLeftLiters)
        // Лічильник уже пройшов через нуль: 200 → 999,9 → 0 → 100 = 900 км, бак порожній.
        val wrapped = FuelCalculator.estimateByOdometer(tripStats(), 100.0) as Estimate.Ok
        assertClose(900.0, wrapped.distanceKm)
        assertClose(0.0, wrapped.fuelLeftLiters)
    }

    @Test
    fun `estimate rejects counter reading at or above rollover`() {
        assertEquals(
            Estimate.Invalid(InputError.ODOMETER_ABOVE_ROLLOVER, 1000.0),
            FuelCalculator.estimateByOdometer(tripStats(), 1000.0),
        )
        assertEquals(Estimate.Invalid(InputError.NEGATIVE), FuelCalculator.estimateByOdometer(tripStats(), -5.0))
    }
}
